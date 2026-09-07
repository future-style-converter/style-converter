package com.styleconverter.runtime.animations

// KeyframeTimeline — the PURE state-at-time math of animation runtime v1
// (schema/spec/07-animations.md §3 timing model + §5 deterministic capture).
//
// Given "wall-clock time t since the animation's timeline zero", this file
// answers ONE question: what DIRECTED iteration progress (0..1) applies at
// t — or null when the animation contributes nothing (outside the active
// interval with no fill). Everything here is plain arithmetic on the
// extracted AnimationConfig vocabulary, deliberately free of Compose state
// so the JVM unit suite can pin every direction / fill / iteration edge.
//
// The composable driver (KeyframeAnimationDriver) supplies t from either
// the live frame clock or the CAPTURE_ANIMATION_TIME forced value — the
// math is identical for both, which is exactly what makes the §5 capture
// contract honest: a seized frame IS the frame a live run would show at t.

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.ceil
import kotlin.math.floor

object KeyframeTimeline {

    /**
     * One animation's timing tuple, index-resolved from AnimationConfig
     * (css-animations-1 §5.2 list matching is the caller's job).
     *
     * @property iterations iteration count; [Double.POSITIVE_INFINITY] for
     *   `infinite`. Fractional counts are honored per §5.5.
     */
    data class Spec(
        val durationMs: Double,
        val delayMs: Double = 0.0,
        val iterations: Double = 1.0,
        val direction: AnimationDirection = AnimationDirection.NORMAL,
        val fillMode: AnimationFillMode = AnimationFillMode.NONE,
        val timing: TimingFunctionConfig = TimingFunctionConfig.EASE
    )

    /** Build a [Spec] for animation index [i] from the extracted config —
     *  one place owns the index-fallback pairing (§3 list matching). */
    fun specAt(config: AnimationConfig, i: Int): Spec = Spec(
        durationMs = config.getDuration(i).toDouble(),
        delayMs = config.getDelay(i).toDouble(),
        iterations = when (val c = config.getIterationCount(i)) {
            is AnimationIterationCount.Infinite -> Double.POSITIVE_INFINITY
            is AnimationIterationCount.Count -> c.value
        },
        direction = config.getDirection(i),
        fillMode = config.getFillMode(i),
        timing = config.getTimingFunction(i)
    )

    /** The end of the active interval in ms (delay + duration × count), or
     *  +∞ for infinite animations — the live clock runs until the LAST
     *  animation on the component passes this point. */
    fun activeEndMs(spec: Spec): Double =
        if (spec.iterations.isInfinite()) Double.POSITIVE_INFINITY
        else spec.delayMs + spec.durationMs * spec.iterations

    /**
     * DIRECTED iteration progress at absolute time [tMs] (0..1, after the
     * §5.6 direction mapping but BEFORE any easing), or null when the base
     * styles apply unchanged (before/after phase without the matching
     * fill — css-animations-1 §4.8 fill semantics).
     *
     * Easing is deliberately NOT applied here: per [css-animations-1]
     * §4.4 the timing function applies BETWEEN KEYFRAMES (each segment
     * eases its own local fraction), so KeyframeInterpolator owns the
     * ease step. Easing the whole iteration instead skewed every
     * multi-stop track (the MK_Pulse t=0.5 capture: web sits exactly on
     * the 50% stop, whole-iteration `ease` landed at 0.80 → scale 1.24
     * vs web's 1.6 — Android-web 0.786).
     */
    fun directedProgressAt(tMs: Double, spec: Spec): Double? {
        // Local time on the animation's own clock; negative delays advance
        // the start point per §5.4 (local jumps straight into the interval).
        val local = tMs - spec.delayMs
        // Active duration: 0s duration completes instantly but fill-mode
        // still applies (§5.5 note — pinned by the spec-07 timing table).
        // The zero-duration guard also keeps 0s × infinite from producing
        // NaN (0 × ∞) — a zero-duration animation is always "instantly done".
        val activeDuration = when {
            spec.durationMs <= 0.0 -> 0.0
            spec.iterations.isInfinite() -> Double.POSITIVE_INFINITY
            else -> spec.durationMs * spec.iterations
        }

        // BEFORE phase — only fill backwards/both paints the first frame.
        if (local < 0.0) {
            val fills = spec.fillMode == AnimationFillMode.BACKWARDS ||
                spec.fillMode == AnimationFillMode.BOTH
            if (!fills) return null
            // The first frame is iteration 0 at progress 0, direction-mapped
            // (reverse families start at 1 — §5.6).
            return directed(0.0, 0, spec.direction)
        }

        // AFTER phase — only fill forwards/both keeps the last frame.
        // (>= so a zero active duration lands here immediately.)
        if (local >= activeDuration) {
            val fills = spec.fillMode == AnimationFillMode.FORWARDS ||
                spec.fillMode == AnimationFillMode.BOTH
            if (!fills) return null
            // Overall progress at the end boundary equals the iteration
            // count itself (web-animations-1 §4.7.3 end-of-animation rule):
            // integer counts end a whole iteration (progress 1 in the LAST
            // iteration), fractional counts end mid-iteration.
            val overall = spec.iterations
            if (overall.isInfinite()) return null // unreachable end of infinite
            val wholeEnd = overall > 0.0 && floor(overall) == overall
            val iteration = if (wholeEnd) (overall - 1.0).toInt() else floor(overall).toInt()
            val iterProgress = if (wholeEnd) 1.0 else overall - floor(overall)
            return directed(iterProgress, iteration, spec.direction)
        }

        // ACTIVE phase — split local time into iteration index + progress.
        if (spec.durationMs <= 0.0) return null // guarded above; belt+braces
        val overall = local / spec.durationMs
        // Clamp the index so t exactly at a whole-iteration boundary inside
        // the interval counts as the START of the next iteration (matching
        // the browser's frame-sampling behaviour at boundaries).
        val iteration = floor(overall).toInt().coerceAtMost(
            if (spec.iterations.isInfinite()) Int.MAX_VALUE else (ceil(spec.iterations) - 1).toInt().coerceAtLeast(0)
        )
        val iterProgress = (overall - floor(overall))
        return directed(iterProgress, iteration, spec.direction)
    }

    /**
     * Map raw iteration progress to DIRECTED progress (§5.6): reverse
     * families flip the axis, alternate families flip on odd/even parity.
     */
    internal fun directed(progress: Double, iteration: Int, direction: AnimationDirection): Double {
        val forwards = when (direction) {
            AnimationDirection.NORMAL -> true
            AnimationDirection.REVERSE -> false
            // alternate: even iterations run forward (§5.6).
            AnimationDirection.ALTERNATE -> iteration % 2 == 0
            // alternate-reverse: odd iterations run forward.
            AnimationDirection.ALTERNATE_REVERSE -> iteration % 2 == 1
        }
        return if (forwards) progress else 1.0 - progress
    }

    /**
     * Evaluate the property-level timing function at fraction p. Called
     * PER KEYFRAME SEGMENT by KeyframeInterpolator ([css-animations-1]
     * §4.4 — in-stop timing functions are runtime-v1-optional, so the
     * property-level function applies to every segment) and across the
     * single old→new segment of a transition flight (spec 07 §4).
     */
    internal fun ease(timing: TimingFunctionConfig, p: Double): Double {
        val clamped = p.coerceIn(0.0, 1.0)
        // steps(n, position) — css-easing-1 §2.3 step positions. Implemented
        // here (not via Compose Easing) because Compose has no step easing.
        val n = timing.stepsCount
        if (n != null && n > 0) {
            return when (timing.stepsPosition?.lowercase()) {
                // jump-start/start: the first jump happens AT 0.
                "start", "jump-start" -> (floor(clamped * n) + 1.0).coerceAtMost(n.toDouble()) / n
                // jump-both: n+1 jumps, both endpoints jump.
                //
                // The clamp is to JUMPS (n+1), not to the step count. The
                // spec's step algorithm reads "if input progress value ≤ 1
                // and current step > jumps, decrement current step by one",
                // and for jump-both jumps = n + 1. Clamping to n instead
                // capped the output at n/(n+1), so a jump-both animation
                // never reached its end state — steps(2, jump-both) sat at
                // 0.667 at t=1 instead of 1.0, and stuck there under
                // animation-fill-mode: forwards. Caught by
                // EasingReferenceTest against the css-easing-1 table.
                "jump-both" -> (floor(clamped * n) + 1.0).coerceAtMost(n + 1.0) / (n + 1.0)
                // jump-none: n-1 interior jumps, endpoints held.
                "jump-none" ->
                    if (n <= 1) clamped // degenerate steps(1, jump-none): identity hold
                    else (floor(clamped * n)).coerceAtMost(n - 1.0) / (n - 1.0)
                // jump-end/end (and unspecified): the last jump happens AT 1.
                else -> if (clamped >= 1.0) 1.0 else floor(clamped * n) / n
            }
        }
        // cubic-bezier — including the keyword presets, which the extractor
        // already normalizes to control points (TimingFunctionConfig.EASE…).
        val cb = timing.cubicBezier
        if (cb != null && cb.size == 4) {
            // Endpoints are exact by definition (css-easing-1 §2.2) — skip
            // the solver so t=0/t=1 pins are bit-exact.
            if (clamped <= 0.0) return 0.0
            if (clamped >= 1.0) return 1.0
            // linear (0,0,1,1) is the identity — return it EXACTLY instead
            // of round-tripping through the float bezier solver (whose
            // ~1e-7 error would smear the motion suite's linear one-clock
            // pins; web computes linear exactly, parity demands we do too).
            if (cb[0] == 0.0 && cb[1] == 0.0 && cb[2] == 1.0 && cb[3] == 1.0) return clamped
            return CubicBezierEasing(
                cb[0].toFloat(), cb[1].toFloat(), cb[2].toFloat(), cb[3].toFloat()
            ).transform(clamped.toFloat()).toDouble()
        }
        // Keyword-only carrier (extractor couldn't normalize) → the spec
        // default `ease` control points, mirroring TimingFunctionConfig.EASE.
        return when (timing.original?.lowercase()) {
            "linear", "step-start", "step-end" -> clamped // step keywords normalize via stepsCount above
            else -> {
                if (clamped <= 0.0) 0.0
                else if (clamped >= 1.0) 1.0
                else CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f).transform(clamped.toFloat()).toDouble()
            }
        }
    }
}
