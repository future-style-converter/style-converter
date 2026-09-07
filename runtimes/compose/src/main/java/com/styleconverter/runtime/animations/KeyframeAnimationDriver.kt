package com.styleconverter.runtime.animations

// KeyframeAnimationDriver — EXECUTES the document-level wire keyframes
// (schema/spec/07-animations.md) inside the Compose renderer.
//
// Division of labor:
//   - KeyframeTimeline     : pure state-at-time math (§3 timing model).
//   - KeyframeInterpolator : pure tier-value math (§2 animatable tier).
//   - THIS file            : the Compose glue — the clock (live frame loop
//     or the CAPTURE_ANIMATION_TIME forced value, §5) and the property
//     overlay that feeds animated values back into the ONE style pipeline.
//
// Design: instead of bolting animated modifiers on top of the static chain
// (the pre-wave-8 approach — a KeyframeAnimationApplier wrapping the whole
// component in a Compose transition, deleted with the rest of the legacy
// path by the retro P2a sweep, A6#6), the driver OVERLAYS the
// interpolated typed properties onto the component's resolved property
// list BEFORE StyleApplier runs. An animated Width/BackgroundColor/
// Transform frame therefore renders through exactly the extractor/applier
// path the SSIM-verified static corpus uses — cross-platform parity is
// inherited, not re-implemented.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRKeyframeStop
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.states.DynamicStyleResolver

object KeyframeAnimationDriver {

    private const val TAG = "KeyframeAnimationDriver"

    /**
     * The document's decoded `keyframes` map (IRDocumentDecoder), provided
     * by the HOST around the render tree — @keyframes are document-scoped
     * in CSS too, so the channel is document-shaped, not component-shaped.
     * Empty = keyframe-free document = identity fast path everywhere.
     */
    val LocalDocumentKeyframes = compositionLocalOf<Map<String, List<IRKeyframeStop>>> { emptyMap() }

    /**
     * CAPTURE_ANIMATION_TIME (spec 07 §5 / docs/DYNAMIC_CAPTURE.md §4), in
     * SECONDS: when non-null, EVERY animation on the surface renders its
     * state at absolute timeline time t, paused — no clock runs at all, so
     * capture latency cannot smear the frame. null = live playback.
     * The harness supplies it from the `animationTime` intent extra.
     */
    val LocalForcedAnimationTime = compositionLocalOf<Double?> { null }

    // Dangling animation-name references already reported (spec 07 §1.3:
    // log once per name — a defined no-op, never an error).
    private val danglingLogged = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * The renderer entry point: return [properties] with animated values
     * overlaid for the current clock. IDENTITY-PRESERVING when the
     * document has no keyframes or the component references none — the
     * static corpus renders byte-identically to the frozen baselines.
     */
    @Composable
    fun animate(properties: List<IRProperty>): List<IRProperty> {
        val doc = LocalDocumentKeyframes.current
        if (doc.isEmpty()) return properties // keyframe-free document

        // Cheap pre-gate before extraction: no AnimationName → no driver.
        if (properties.none { it.type == "AnimationName" }) return properties

        val config = remember(properties) {
            AnimationExtractor.extractAnimationConfig(properties.map { it.type to it.data })
        }
        if (config.names.isEmpty()) return properties

        // Resolve names against the document map. Names are lowercased
        // custom-idents on both sides (spec 07 §1.3) — the fallback lookup
        // covers any pre-normalization authoring path.
        val sets: List<Pair<Int, List<IRKeyframeStop>>> = remember(config, doc) {
            config.names.mapIndexedNotNull { i, name ->
                val stops = doc[name] ?: doc[name.lowercase()]
                if (stops == null) {
                    reportDangling(name) // §1.3 defined no-op + one log
                    null
                } else {
                    i to stops
                }
            }
        }
        if (sets.isEmpty()) return properties // all names dangling → base styles

        val forcedT = LocalForcedAnimationTime.current
        val tMs: Double
        if (forcedT != null) {
            // §5 deterministic capture: state at absolute t, paused. The
            // forced clock also overrides `animation-play-state: paused`
            // (§3: the two compose — the capture puts the animation AT t,
            // which is exactly what the web reference's WAAPI seize does
            // to paused animations too).
            tMs = forcedT * 1000.0
        } else {
            // Live playback: one frame-clock loop per animated component.
            // The clock stops advancing once the LAST timeline leaves its
            // active interval (fill states are time-invariant afterwards);
            // infinite animations keep the loop alive.
            val endMs = remember(config, sets) {
                sets.maxOf { (i, _) -> KeyframeTimeline.activeEndMs(KeyframeTimeline.specAt(config, i)) }
            }
            // `animation-play-state: paused` at load freezes progress at 0
            // (§3: pausing holds the current progress; a never-started
            // clock's progress is its t=0 state). Per-list pause applies
            // to every animation on the component — matching the CSS
            // longhand's list semantics at this driver's v1 granularity.
            val allPaused = remember(config) {
                config.names.indices.all { config.getPlayState(it) == AnimationPlayState.PAUSED }
            }
            var nowMs by remember(config, sets) { mutableDoubleStateOf(0.0) }
            LaunchedEffect(config, sets, allPaused) {
                if (allPaused) return@LaunchedEffect // frozen at t=0
                // Anchor timeline zero at the first frame after composition
                // — the CSS load-time animation start.
                var startNanos = -1L
                while (true) {
                    withFrameNanos { frame ->
                        if (startNanos < 0) startNanos = frame
                        nowMs = (frame - startNanos) / 1e6
                    }
                    if (nowMs >= endMs) {
                        nowMs = endMs // settle exactly on the end state
                        break
                    }
                }
            }
            tMs = nowMs
        }

        // Pure overlay — cached per (inputs, clock tick).
        return remember(properties, config, sets, tMs) {
            applyAt(properties, config, sets, tMs)
        }
    }

    /**
     * PURE core (JVM-testable): overlay every referenced keyframe set's
     * interpolated values at absolute time [tMs] onto [base]. Sets apply
     * in animation-list order, so a later animation overrides an earlier
     * one on shared properties — the css-animations-1 §5.2 list cascade.
     */
    fun applyAt(
        base: List<IRProperty>,
        config: AnimationConfig,
        sets: List<Pair<Int, List<IRKeyframeStop>>>,
        tMs: Double
    ): List<IRProperty> {
        var out = base
        for ((index, stops) in sets) {
            val spec = KeyframeTimeline.specAt(config, index)
            // null progress = outside the active interval with no fill —
            // this animation contributes nothing at t (base shows through).
            // The progress is DIRECTED but un-eased; the interpolator
            // applies spec.timing per keyframe segment (§4.4).
            val progress = KeyframeTimeline.directedProgressAt(tMs, spec) ?: continue
            val overrides = KeyframeInterpolator.overridesAt(stops, progress, base, spec.timing)
            if (overrides.isEmpty()) continue
            // Same overlay primitive the bucket fold uses (spec 06 §3
            // whole-value replacement) — one merge semantic everywhere.
            out = DynamicStyleResolver.overlay(out, overrides)
        }
        return out
    }

    /** Spec 07 §1.3: dangling animation-name = defined no-op, logged once
     *  per name via PropertyTracker + logcat. Never an error. */
    private fun reportDangling(name: String) {
        PropertyTracker.markUnhandled("Keyframes[$name]")
        if (danglingLogged.add(name)) {
            try {
                android.util.Log.w(TAG, "animation-name '$name' has no @keyframes set in this document — defined no-op (spec 07 §1.3)")
            } catch (_: RuntimeException) {
                // Plain-JVM unit tests: android.util.Log is a stub.
            }
        }
    }

    /** Test seam: clear the once-per-name dangling log so unit tests can
     *  assert the report fires (process-level sets outlive test methods). */
    internal fun resetDanglingLogForTest() = danglingLogged.clear()
}
