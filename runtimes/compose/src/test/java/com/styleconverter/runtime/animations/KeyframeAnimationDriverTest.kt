package com.styleconverter.runtime.animations

// End-to-end pinning of the driver's PURE core: extracted wire config +
// wire keyframe stops + absolute time → the overlaid property list the
// renderer feeds StyleApplier. These are the exact scenarios the motion
// visual suite captures at t=0 / t=mid (spec 07 §5 one-clock contract),
// pinned here in property-list space so a capture regression can be
// bisected to math vs harness.

import com.styleconverter.runtime.core.ir.IRKeyframeStop
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class KeyframeAnimationDriverTest {

    private val EPS = 1e-9
    private fun el(s: String) = Json.parseToJsonElement(s)

    // The MK_Fade shape: 1s linear fade with fill both.
    private fun fadeConfig(delayMs: Long = 0L) = AnimationConfig(
        names = listOf("motion-fade"),
        durations = listOf(1000L),
        delays = if (delayMs != 0L) listOf(delayMs) else emptyList(),
        timingFunctions = listOf(TimingFunctionConfig.LINEAR),
        fillModes = listOf(AnimationFillMode.BOTH),
        hasAnimations = true
    )

    private val fadeStops = listOf(
        IRKeyframeStop(0.0, listOf(IRProperty("Opacity", el("""{"alpha":0.0}""")))),
        IRKeyframeStop(1.0, listOf(IRProperty("Opacity", el("""{"alpha":1.0}"""))))
    )

    private val base = listOf(
        IRProperty("Width", el("""{"type":"length","px":120.0}""")),
        IRProperty("BackgroundColor", el("""{"srgb":{"r":0.5,"g":0.1,"b":0.1}}"""))
    )

    private fun alphaOf(props: List<IRProperty>): Double =
        ((props.first { it.type == "Opacity" }.data) as JsonObject)["alpha"]!!
            .jsonPrimitive.doubleOrNull!!

    @Test
    fun `state at t0 and mid overlay the interpolated opacity onto base`() {
        val sets = listOf(0 to fadeStops)
        val at0 = KeyframeAnimationDriver.applyAt(base, fadeConfig(), sets, 0.0)
        assertEquals(0.0, alphaOf(at0), EPS)
        val atMid = KeyframeAnimationDriver.applyAt(base, fadeConfig(), sets, 500.0)
        assertEquals(0.5, alphaOf(atMid), EPS)
        // Base declarations ride through untouched (overlay, not replace).
        assertEquals("Width", atMid[0].type)
        assertEquals(3, atMid.size)
    }

    @Test
    fun `fill both plus delay pins the MK_FillBoth capture scenario`() {
        // MK_FillBoth: 1s fade, 0.5s delay, fill both. t=0 must paint the
        // FROM frame (opacity 0), not base — the DYNAMIC_CAPTURE §4 pin.
        val sets = listOf(0 to fadeStops)
        val at0 = KeyframeAnimationDriver.applyAt(base, fadeConfig(delayMs = 500L), sets, 0.0)
        assertEquals(0.0, alphaOf(at0), EPS)
        // t=0.75s = 0.25 into the run.
        val atQ = KeyframeAnimationDriver.applyAt(base, fadeConfig(delayMs = 500L), sets, 750.0)
        assertEquals(0.25, alphaOf(atQ), EPS)
    }

    @Test
    fun `outside the interval with fill none the base list returns unchanged`() {
        val config = fadeConfig().copy(fillModes = listOf(AnimationFillMode.NONE))
        val sets = listOf(0 to fadeStops)
        val after = KeyframeAnimationDriver.applyAt(base, config, sets, 5000.0)
        // SAME instance — the identity guarantee that keeps remember()
        // keys stable for the static corpus.
        assertSame(base, after)
    }

    @Test
    fun `later animations in the list win shared properties`() {
        // css-animations-1 §5.2: 'a, b' — b's opacity beats a's at overlap.
        val dimStops = listOf(
            IRKeyframeStop(0.0, listOf(IRProperty("Opacity", el("""{"alpha":0.8}""")))),
            IRKeyframeStop(1.0, listOf(IRProperty("Opacity", el("""{"alpha":0.8}"""))))
        )
        val config = AnimationConfig(
            names = listOf("fade", "dim"),
            durations = listOf(1000L, 1000L),
            timingFunctions = listOf(TimingFunctionConfig.LINEAR),
            fillModes = listOf(AnimationFillMode.BOTH),
            hasAnimations = true
        )
        val sets = listOf(0 to fadeStops, 1 to dimStops)
        val mid = KeyframeAnimationDriver.applyAt(base, config, sets, 500.0)
        assertEquals(0.8, alphaOf(mid), EPS)
    }

    @Test
    fun `dangling name resolution is a logged no-op`() {
        // The driver-side twin of the wire's GhostBox golden (spec 07
        // §1.3): a name with no set contributes nothing and marks the
        // tracker once. Resolution happens in the composable; the pure
        // equivalent is an empty set list → applyAt returns base as-is.
        com.styleconverter.runtime.PropertyTracker.reset()
        KeyframeAnimationDriver.resetDanglingLogForTest()
        val out = KeyframeAnimationDriver.applyAt(base, fadeConfig(), emptyList(), 500.0)
        assertSame(base, out)
    }
}
