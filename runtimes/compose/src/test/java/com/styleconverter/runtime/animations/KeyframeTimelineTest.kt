package com.styleconverter.runtime.animations

// State-at-t pinning for the pure timeline math (spec 07 §3 timing model +
// the §5 CAPTURE_ANIMATION_TIME semantics). Each pin mirrors a motion-
// fixture scenario (fixtures/fidelity/motion/keyframes-basic.json) so the
// visual captures and the unit suite agree on the same arithmetic.
//
// The timeline returns DIRECTED (un-eased) progress — easing applies per
// keyframe SEGMENT in KeyframeInterpolator (css-animations-1 §4.4); the
// ease() pins at the bottom exercise the shared evaluator directly.

import com.styleconverter.runtime.animations.KeyframeTimeline.Spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyframeTimelineTest {

    private val EPS = 1e-9

    // Linear 1s one-shot — the motion suite's "one clock" baseline shape.
    private fun linear1s(fill: AnimationFillMode = AnimationFillMode.BOTH) = Spec(
        durationMs = 1000.0,
        fillMode = fill,
        timing = TimingFunctionConfig.LINEAR
    )

    // ── active-phase basics ──────────────────────────────────────────────

    @Test
    fun `linear progress at t0, mid and end`() {
        val spec = linear1s()
        assertEquals(0.0, KeyframeTimeline.directedProgressAt(0.0, spec)!!, EPS)
        assertEquals(0.5, KeyframeTimeline.directedProgressAt(500.0, spec)!!, EPS)
        // t = duration is the AFTER boundary; fill both keeps the end frame.
        assertEquals(1.0, KeyframeTimeline.directedProgressAt(1000.0, spec)!!, EPS)
    }

    @Test
    fun `fill none contributes nothing outside the active interval`() {
        val spec = linear1s(fill = AnimationFillMode.NONE)
        // After the interval with no forwards fill → base styles (null).
        assertNull(KeyframeTimeline.directedProgressAt(1500.0, spec))
        // Before the interval (delay pushes the start) with no backwards
        // fill → base styles too.
        val delayed = spec.copy(delayMs = 500.0)
        assertNull(KeyframeTimeline.directedProgressAt(100.0, delayed))
    }

    // ── delay + fill (the MK_FillBoth pin: 1s duration, 0.5s delay, both) ─

    @Test
    fun `fill both with delay - t0 is the from-state, not base`() {
        val spec = linear1s().copy(delayMs = 500.0)
        // DYNAMIC_CAPTURE §4: t=0 with backwards fill is the FROM frame.
        assertEquals(0.0, KeyframeTimeline.directedProgressAt(0.0, spec)!!, EPS)
        // Mid-delay still holds the from-state.
        assertEquals(0.0, KeyframeTimeline.directedProgressAt(400.0, spec)!!, EPS)
        // 0.75s = 0.25s into the 1s run.
        assertEquals(0.25, KeyframeTimeline.directedProgressAt(750.0, spec)!!, EPS)
        // Past the end, forwards fill holds the to-state.
        assertEquals(1.0, KeyframeTimeline.directedProgressAt(2000.0, spec)!!, EPS)
    }

    @Test
    fun `negative delay advances the start point`() {
        val spec = linear1s().copy(delayMs = -250.0)
        // css-animations-1 §5.4: at t=0 the animation is already 0.25 in.
        assertEquals(0.25, KeyframeTimeline.directedProgressAt(0.0, spec)!!, EPS)
    }

    // ── direction (the MK_Alternate pin: 2 iterations, alternate) ───────

    @Test
    fun `alternate direction flips odd iterations`() {
        val spec = linear1s().copy(iterations = 2.0, direction = AnimationDirection.ALTERNATE)
        // Iteration 0 (forward): 0.5s → 0.5.
        assertEquals(0.5, KeyframeTimeline.directedProgressAt(500.0, spec)!!, EPS)
        // Iteration 1 (backward): 1.5s → 1 − 0.5 = 0.5 (same value — the
        // fixture's distinct-geometry pin uses asymmetric t for real diffs).
        assertEquals(0.5, KeyframeTimeline.directedProgressAt(1500.0, spec)!!, EPS)
        // 1.75s → iteration 1 at 0.75 → directed 0.25.
        assertEquals(0.25, KeyframeTimeline.directedProgressAt(1750.0, spec)!!, EPS)
        // End of 2 alternate iterations lands back at 0 (fill both).
        assertEquals(0.0, KeyframeTimeline.directedProgressAt(2000.0, spec)!!, EPS)
    }

    @Test
    fun `reverse plays one to zero`() {
        val spec = linear1s().copy(direction = AnimationDirection.REVERSE)
        assertEquals(1.0, KeyframeTimeline.directedProgressAt(0.0, spec)!!, EPS)
        assertEquals(0.75, KeyframeTimeline.directedProgressAt(250.0, spec)!!, EPS)
        // Reverse ENDS at directed 0 — forwards fill holds that.
        assertEquals(0.0, KeyframeTimeline.directedProgressAt(1000.0, spec)!!, EPS)
    }

    @Test
    fun `alternate-reverse starts backward`() {
        val spec = linear1s().copy(iterations = 2.0, direction = AnimationDirection.ALTERNATE_REVERSE)
        // Iteration 0 runs backward: 0.25s → 0.75.
        assertEquals(0.75, KeyframeTimeline.directedProgressAt(250.0, spec)!!, EPS)
        // Iteration 1 runs forward: 1.25s → 0.25.
        assertEquals(0.25, KeyframeTimeline.directedProgressAt(1250.0, spec)!!, EPS)
    }

    // ── iteration arithmetic ─────────────────────────────────────────────

    @Test
    fun `fractional iteration count ends mid-iteration`() {
        val spec = linear1s().copy(iterations = 1.5)
        // Active until 1.5s; at/after that, forwards fill holds 0.5.
        assertEquals(0.5, KeyframeTimeline.directedProgressAt(1500.0, spec)!!, EPS)
        assertEquals(0.5, KeyframeTimeline.directedProgressAt(9999.0, spec)!!, EPS)
    }

    @Test
    fun `infinite iterations keep cycling`() {
        val spec = linear1s().copy(iterations = Double.POSITIVE_INFINITY)
        // 10.25s → iteration 10 at 0.25.
        assertEquals(0.25, KeyframeTimeline.directedProgressAt(10250.0, spec)!!, EPS)
        // activeEndMs is unreachable — the live clock never stops.
        assertEquals(Double.POSITIVE_INFINITY, KeyframeTimeline.activeEndMs(spec), 0.0)
    }

    @Test
    fun `zero duration completes instantly but fill still applies`() {
        // Spec 07 §3 bullet 1: 0s means instantly complete, fill applies.
        val spec = Spec(durationMs = 0.0, fillMode = AnimationFillMode.FORWARDS,
            timing = TimingFunctionConfig.LINEAR)
        assertEquals(1.0, KeyframeTimeline.directedProgressAt(0.0, spec)!!, EPS)
        // …and with no fill, nothing ever paints.
        assertNull(KeyframeTimeline.directedProgressAt(0.0, spec.copy(fillMode = AnimationFillMode.NONE)))
    }

    // ── easing ───────────────────────────────────────────────────────────

    @Test
    fun `timeline progress is un-eased - the timing function never warps offsets`() {
        // §4.4: keyframe offsets sit at LINEAR time positions; easing is a
        // per-segment concern. A non-linear property timing function must
        // NOT change the timeline's directed progress.
        val spec = linear1s().copy(timing = TimingFunctionConfig.EASE_IN)
        assertEquals(0.5, KeyframeTimeline.directedProgressAt(500.0, spec)!!, EPS)
    }

    @Test
    fun `ease evaluates cubic-bezier with exact endpoints`() {
        // ease-in (0.42,0,1,1) at p=0.5 must be strictly below linear.
        val eased = KeyframeTimeline.ease(TimingFunctionConfig.EASE_IN, 0.5)
        org.junit.Assert.assertTrue("ease-in(0.5)=$eased should be < 0.5", eased < 0.5)
        // Endpoints stay exact (css-easing-1 §2.2) — the t=0/t=end capture
        // pins depend on bit-exact endpoint math.
        assertEquals(0.0, KeyframeTimeline.ease(TimingFunctionConfig.EASE_IN, 0.0), EPS)
        assertEquals(1.0, KeyframeTimeline.ease(TimingFunctionConfig.EASE_IN, 1.0), EPS)
        // linear (0,0,1,1) is the exact identity — never the float solver.
        assertEquals(0.25, KeyframeTimeline.ease(TimingFunctionConfig.LINEAR, 0.25), 0.0)
    }

    @Test
    fun `steps easing quantizes the segment fraction`() {
        val steps4End = TimingFunctionConfig(null, 4, "end", "steps(4, end)")
        // jump-end: value holds below each boundary.
        assertEquals(0.0, KeyframeTimeline.ease(steps4End, 0.1), EPS)
        assertEquals(0.25, KeyframeTimeline.ease(steps4End, 0.3), EPS)
        assertEquals(0.75, KeyframeTimeline.ease(steps4End, 0.99), EPS)
        assertEquals(1.0, KeyframeTimeline.ease(steps4End, 1.0), EPS)
        // jump-start jumps AT zero.
        val steps2Start = TimingFunctionConfig(null, 2, "start", "steps(2, start)")
        assertEquals(0.5, KeyframeTimeline.ease(steps2Start, 0.1), EPS)
    }
}
