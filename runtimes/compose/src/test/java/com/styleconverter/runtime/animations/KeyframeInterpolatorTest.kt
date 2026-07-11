package com.styleconverter.runtime.animations

// Tier-value pinning for the pure interpolation math (spec 07 §2
// animatable tier v1: numbers linear, lengths in px, colors in sRGB) on
// VERBATIM wire byte shapes — every payload below is copy-shaped from
// the conformance golden (schema/conformance/fixtures/v2/keyframes.json)
// and the converted motion suite, so the unit pins and the visual
// captures exercise the same bytes.

import com.styleconverter.runtime.core.ir.IRKeyframeStop
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeInterpolatorTest {

    private val EPS = 1e-9

    private fun el(s: String) = Json.parseToJsonElement(s)
    private fun stop(offset: Double, vararg props: Pair<String, String>) = IRKeyframeStop(
        offset = offset,
        properties = props.map { (t, d) -> IRProperty(t, el(d)) }
    )

    private fun List<IRProperty>.dataOf(type: String): JsonObject? =
        firstOrNull { it.type == type }?.data as? JsonObject

    // ── opacity (motion-fade) ────────────────────────────────────────────

    @Test
    fun `opacity lerps linearly on the alpha carrier`() {
        val stops = listOf(
            stop(0.0, "Opacity" to """{"alpha":0.0,"original":{"type":"number","value":0.0}}"""),
            stop(1.0, "Opacity" to """{"alpha":1.0,"original":{"type":"number","value":1.0}}""")
        )
        val out = KeyframeInterpolator.overridesAt(stops, 0.25, emptyList())
        assertEquals(0.25, out.dataOf("Opacity")!!["alpha"]!!.jsonPrimitive.doubleOrNull!!, EPS)
    }

    // ── lengths in px (motion-steps 3-stop, authored-unsorted upstream) ─

    @Test
    fun `width lerps in px across a 3-stop track`() {
        val stops = listOf(
            stop(0.0, "Width" to """{"type":"length","px":40.0}"""),
            stop(0.5, "Width" to """{"type":"length","px":160.0}"""),
            stop(1.0, "Width" to """{"type":"length","px":90.0}""")
        )
        // Segment 1: 0.25 → halfway 40→160 = 100.
        val mid1 = KeyframeInterpolator.overridesAt(stops, 0.25, emptyList())
        assertEquals(100.0, mid1.dataOf("Width")!!["px"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        // Segment 2: 0.75 → halfway 160→90 = 125.
        val mid2 = KeyframeInterpolator.overridesAt(stops, 0.75, emptyList())
        assertEquals(125.0, mid2.dataOf("Width")!!["px"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        // The emitted shape is the standard length carrier.
        assertEquals("length", mid1.dataOf("Width")!!["type"]!!.jsonPrimitive.content)
    }

    // ── colors in sRGB (motion-color: #3498db → #e67e22) ────────────────

    @Test
    fun `background-color mixes component-wise in sRGB space`() {
        val from = """{"srgb":{"r":0.2,"g":0.6,"b":0.8},"original":"#3498db-ish"}"""
        val to = """{"srgb":{"r":0.9,"g":0.5,"b":0.2},"original":"#e67e22-ish"}"""
        val stops = listOf(stop(0.0, "BackgroundColor" to from), stop(1.0, "BackgroundColor" to to))
        val srgb = KeyframeInterpolator.overridesAt(stops, 0.5, emptyList())
            .dataOf("BackgroundColor")!!["srgb"]!!.jsonObject
        assertEquals(0.55, srgb["r"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        assertEquals(0.55, srgb["g"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        assertEquals(0.5, srgb["b"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        // Opaque mix omits alpha — same carrier the wire emits.
        assertNull(srgb["a"])
    }

    @Test
    fun `alpha defaults to one when only one endpoint carries it`() {
        val stops = listOf(
            stop(0.0, "Color" to """{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.5}}"""),
            stop(1.0, "Color" to """{"srgb":{"r":1.0,"g":1.0,"b":1.0}}""")
        )
        val srgb = KeyframeInterpolator.overridesAt(stops, 0.5, emptyList())
            .dataOf("Color")!!["srgb"]!!.jsonObject
        assertEquals(0.75, srgb["a"]!!.jsonPrimitive.doubleOrNull!!, EPS)
    }

    // ── transforms (motion-slide translateX, motion-pulse scale) ────────

    @Test
    fun `matching translateX lists interpolate the px leaf`() {
        val from = """{"type":"functions","list":[{"fn":"translateX","x":{"px":0.0}}]}"""
        val to = """{"type":"functions","list":[{"fn":"translateX","x":{"px":120.0}}]}"""
        val stops = listOf(stop(0.0, "Transform" to from), stop(1.0, "Transform" to to))
        val out = KeyframeInterpolator.overridesAt(stops, 0.5, emptyList()).dataOf("Transform")!!
        val entry = out["list"]!!.jsonArray[0].jsonObject
        assertEquals("translateX", entry["fn"]!!.jsonPrimitive.content)
        assertEquals(60.0, entry["x"]!!.jsonObject["px"]!!.jsonPrimitive.doubleOrNull!!, EPS)
    }

    @Test
    fun `matching scale lists interpolate bare numeric leaves`() {
        val from = """{"type":"functions","list":[{"fn":"scale","x":1.0,"y":1.0}]}"""
        val to = """{"type":"functions","list":[{"fn":"scale","x":1.6,"y":1.6}]}"""
        val stops = listOf(stop(0.0, "Transform" to from), stop(1.0, "Transform" to to))
        val entry = KeyframeInterpolator.overridesAt(stops, 0.5, emptyList())
            .dataOf("Transform")!!["list"]!!.jsonArray[0].jsonObject
        assertEquals(1.3, entry["x"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        assertEquals(1.3, entry["y"]!!.jsonPrimitive.doubleOrNull!!, EPS)
    }

    @Test
    fun `timing eases per segment - offsets are hit exactly (MK_Pulse pin)`() {
        // css-animations-1 §4.4: the timing function applies BETWEEN
        // keyframes. MK_Pulse (scale 1 → 1.6@50% → 1, default `ease`) at
        // directed progress 0.5 sits EXACTLY on the 50% stop — whole-
        // iteration easing instead landed at eased 0.80 → scale 1.24 and
        // cost the t=0.5 capture 0.786 SSIM vs web.
        val stops = listOf(
            stop(0.0, "Transform" to """{"type":"functions","list":[{"fn":"scale","x":1.0,"y":1.0}]}"""),
            stop(0.5, "Transform" to """{"type":"functions","list":[{"fn":"scale","x":1.6,"y":1.6}]}"""),
            stop(1.0, "Transform" to """{"type":"functions","list":[{"fn":"scale","x":1.0,"y":1.0}]}""")
        )
        val atStop = KeyframeInterpolator.overridesAt(stops, 0.5, emptyList(), TimingFunctionConfig.EASE)
            .dataOf("Transform")!!["list"]!!.jsonArray[0].jsonObject
        assertEquals(1.6, atStop["x"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        // Mid-segment the ease curve leads linear: at p=0.25 the local
        // fraction is 0.5 and ease(0.5) > 0.5, so scale > the linear 1.3.
        val midSeg = KeyframeInterpolator.overridesAt(stops, 0.25, emptyList(), TimingFunctionConfig.EASE)
            .dataOf("Transform")!!["list"]!!.jsonArray[0].jsonObject
        assertTrue(midSeg["x"]!!.jsonPrimitive.doubleOrNull!! > 1.3)
    }

    @Test
    fun `non-matching transform lists step at the boundary`() {
        // css-transforms-1: rotate vs translateX don't pair — the §2
        // discrete step rule applies (prev value until the next offset).
        val from = """{"type":"functions","list":[{"fn":"rotate","a":{"deg":0.0}}]}"""
        val to = """{"type":"functions","list":[{"fn":"translateX","x":{"px":50.0}}]}"""
        val stops = listOf(stop(0.0, "Transform" to from), stop(1.0, "Transform" to to))
        val mid = KeyframeInterpolator.overridesAt(stops, 0.6, emptyList()).dataOf("Transform")!!
        assertEquals("rotate", mid["list"]!!.jsonArray[0].jsonObject["fn"]!!.jsonPrimitive.content)
        val end = KeyframeInterpolator.overridesAt(stops, 1.0, emptyList()).dataOf("Transform")!!
        assertEquals("translateX", end["list"]!!.jsonArray[0].jsonObject["fn"]!!.jsonPrimitive.content)
    }

    // ── §4.1 implicit endpoints (the golden's slide-shift Width-at-50%) ─

    @Test
    fun `missing edge stops synthesize from the base computed value`() {
        val base = listOf(IRProperty("Width", el("""{"type":"length","px":80.0}""")))
        val stops = listOf(
            stop(0.0, "Opacity" to """{"alpha":0.0}"""),
            stop(0.5, "Width" to """{"type":"length","px":140.0}"""),
            stop(1.0, "Opacity" to """{"alpha":1.0}""")
        )
        // p=0.25: base 80 → 140 halfway = 110.
        val q1 = KeyframeInterpolator.overridesAt(stops, 0.25, base)
        assertEquals(110.0, q1.dataOf("Width")!!["px"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        // p=0.75: 140 → base 80 halfway = 110 again (symmetric).
        val q3 = KeyframeInterpolator.overridesAt(stops, 0.75, base)
        assertEquals(110.0, q3.dataOf("Width")!!["px"]!!.jsonPrimitive.doubleOrNull!!, EPS)
    }

    // ── non-tier step-apply (§2 honesty valve) ──────────────────────────

    @Test
    fun `non-tier properties step-apply at stop boundaries and are logged`() {
        com.styleconverter.runtime.PropertyTracker.reset()
        val stops = listOf(
            stop(0.0, "BorderTopLeftRadius" to """{"type":"length","px":0.0}"""),
            stop(1.0, "BorderTopLeftRadius" to """{"type":"length","px":20.0}""")
        )
        // Mid-run: the PREVIOUS stop's value holds (boundary switching).
        val mid = KeyframeInterpolator.overridesAt(stops, 0.5, emptyList())
        assertEquals(0.0, mid.dataOf("BorderTopLeftRadius")!!["px"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        // At the boundary the next value applies.
        val end = KeyframeInterpolator.overridesAt(stops, 1.0, emptyList())
        assertEquals(20.0, end.dataOf("BorderTopLeftRadius")!!["px"]!!.jsonPrimitive.doubleOrNull!!, EPS)
        // §2 MUST: the step is visible to fidelity reports via the tracker.
        assertTrue(com.styleconverter.runtime.PropertyTracker
            .isUnhandled("KeyframeStep[BorderTopLeftRadius]"))
    }

    // ── equal-offset last-wins (stable-sort cascade, §1.2) ──────────────

    @Test
    fun `equal offsets resolve last-wins per property`() {
        val stops = listOf(
            stop(0.0, "Opacity" to """{"alpha":0.1}"""),
            stop(0.0, "Opacity" to """{"alpha":0.9}"""), // same offset, later author order
            stop(1.0, "Opacity" to """{"alpha":0.9}""")
        )
        val out = KeyframeInterpolator.overridesAt(stops, 0.0, emptyList())
        assertEquals(0.9, out.dataOf("Opacity")!!["alpha"]!!.jsonPrimitive.doubleOrNull!!, EPS)
    }

    // ── runtime-dependent lengths step (null px escape) ─────────────────

    @Test
    fun `null px lengths fall back to the step rule`() {
        val stops = listOf(
            stop(0.0, "Width" to """{"type":"length","px":null,"original":"50%"}"""),
            stop(1.0, "Width" to """{"type":"length","px":90.0}""")
        )
        val mid = KeyframeInterpolator.overridesAt(stops, 0.5, emptyList()).dataOf("Width")!!
        // Prev (the unresolved 50%) holds until the boundary — honest step,
        // never an invented pixel value.
        assertEquals("50%", mid["original"]!!.jsonPrimitive.content)
    }
}
