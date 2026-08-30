package com.styleconverter.runtime.color

// Wave-49 lane A5 — static `color-mix()` evaluation (css-color-5 §3).
//
// Every wire payload below is copied VERBATIM out of the wave-48 corpus run:
//   tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//     wpt__css-color__color-mix-currentcolor-001.json  (in srgb, currentColor)
//     wpt__css-color__color-mix-currentcolor-002.json  (in lch,  currentColor)
//     wpt__css-color__color-mix-percents-01.json       (in lch, purple/plum,
//                                                       all six percent forms)
//     wpt__css-color__color-mix-percents-02.json       (the 125% / 9999%
//                                                       out-of-range stripes)
//
// The expected colours are NOT fitted to our own output: they are the
// reference PNG's pixels and the values the WPT tests themselves assert.
//   - color-mix(in lch, green 50%, blue) must be sRGB (0, 117, 189) — the
//     pixel at (50,50) of tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c9
//     99cef1/white-black-ink-font-lh-imgpad-htmlpins/css-color/
//     color-mix-currentcolor-002.png.
//   - every valid stripe of color-mix-percents-01 must be the colour the
//     test's own .t1 control declares: rgb(68.4898% 36.015% 68.3102%).

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class StaticColorMixTest {

    private fun wire(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    /** The payload extractor accepts the `{original:{…}}` envelope. */
    private fun mix(json: String) =
        StaticColorMix.payload(wire("""{"original":$json}"""))!!

    /** sRGB byte comparison — the unit the reference PNGs are stored in. */
    private fun assertBytes(expected: Triple<Int, Int, Int>, actual: Color?, tol: Int = 1) {
        assertNotNull("expected a resolved colour", actual)
        val r = Math.round(actual!!.red * 255f)
        val g = Math.round(actual.green * 255f)
        val b = Math.round(actual.blue * 255f)
        val ok = abs(r - expected.first) <= tol &&
            abs(g - expected.second) <= tol &&
            abs(b - expected.third) <= tol
        assertEquals("rgb($r,$g,$b) vs expected rgb(${expected.first}," +
            "${expected.second},${expected.third})", true, ok)
    }

    // The purple every valid color-mix-percents-01 stripe must land on:
    // rgb(68.4898% 36.015% 68.3102%) → (174.65, 91.84, 174.19).
    private val PERCENTS_PURPLE = Triple(175, 92, 174)

    // Compose packs an sRGB Color into 32 bits — EIGHT bits per channel,
    // alpha included (androidx.compose.ui.graphics.Color's fast path for
    // ColorSpaces.Srgb). So a stored 0.5 reads back as 128/255 = 0.5019608,
    // and one byte is the tightest honest tolerance for an alpha assertion.
    private val ONE_BYTE = 1f / 255f

    // ── §6.4 substitution ────────────────────────────────────────────────

    @Test fun `in srgb with currentColor takes the element's own color`() {
        // color-mix-currentcolor-001's CHILD sees the parent's mix through
        // `background-color: inherit` and re-resolves it against its own
        // green: color-mix(in srgb, green 50%, green) = green.
        val green = Color(0f, 0.5019608f, 0f, 1f)
        val out = StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"srgb","color1":"currentColor","percent1":50,"color2":"green"}"""),
            green,
        )
        assertBytes(Triple(0, 128, 0), out)
    }

    @Test fun `in srgb with currentColor red is the parent's brown`() {
        // The same wire on the PARENT (color: red) — Chrome 151 computes
        // color(srgb 0.5 0.25098 0), verified with getComputedStyle.
        val out = StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"srgb","color1":"currentColor","percent1":50,"color2":"green"}"""),
            Color(1f, 0f, 0f, 1f),
        )
        assertBytes(Triple(128, 64, 0), out)
    }

    @Test fun `in lch with currentColor green matches the reference pixel`() {
        // color-mix-currentcolor-002's child: color-mix(in lch, green 50%,
        // blue). Chrome 151: lch(37.9213 99.596 217.874); the frozen
        // reference PNG's pixel is rgb(0,117,189).
        val out = StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"currentColor","percent1":50,"color2":"blue"}"""),
            Color(0f, 0.5019608f, 0f, 1f),
        )
        assertBytes(Triple(0, 117, 189), out)
    }

    @Test fun `currentColor endpoint with no own color stays unresolved`() {
        // Nothing to substitute — an honest blank beats a guessed ink.
        assertNull(StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"srgb","color1":"currentColor","percent1":50,"color2":"green"}"""),
            null,
        ))
    }

    // ── Percentage normalisation (css-color-5 §3.2) ──────────────────────
    // All six valid forms of color-mix-percents-01 must produce ONE colour.

    @Test fun `both percentages given`() {
        assertBytes(PERCENTS_PURPLE, StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":50,"color2":"plum","percent2":50}"""),
            null))
    }

    @Test fun `second percentage omitted defaults to the complement`() {
        assertBytes(PERCENTS_PURPLE, StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":50,"color2":"plum"}"""),
            null))
    }

    @Test fun `first percentage omitted defaults to the complement`() {
        assertBytes(PERCENTS_PURPLE, StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","color2":"plum","percent2":50}"""),
            null))
    }

    @Test fun `both omitted default to fifty fifty`() {
        assertBytes(PERCENTS_PURPLE, StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","color2":"plum"}"""),
            null))
    }

    @Test fun `endpoint order does not change a fifty fifty mix`() {
        // …-01's .t6 swaps the operands; hue arc selection must be symmetric.
        assertBytes(PERCENTS_PURPLE, StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"plum","color2":"purple"}"""),
            null))
    }

    @Test fun `sum above one hundred renormalises without touching alpha`() {
        // …-01's .t7: 80% + 80% → 50/50, alpha unchanged (§2.1 only scales
        // alpha when the sum is BELOW 100%).
        val out = StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":80,"color2":"plum","percent2":80}"""),
            null)
        assertBytes(PERCENTS_PURPLE, out)
        assertEquals(1f, out!!.alpha, ONE_BYTE)
    }

    @Test fun `sum below one hundred scales the result alpha`() {
        // §2.1: 25% + 25% → weights 50/50 and alpha × 0.5.
        val out = StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":25,"color2":"plum","percent2":25}"""),
            null)
        assertBytes(PERCENTS_PURPLE, out)
        assertEquals(0.5f, out!!.alpha, ONE_BYTE)
    }

    @Test fun `percentages outside zero to one hundred are invalid`() {
        // color-mix-percents-02's .t6/.t7. The grammar is
        // <percentage [0,100]>: out of range invalidates the declaration,
        // it is NOT clamped (clamping would paint the 9999% stripe the same
        // purple as the valid ones and hide a real divergence).
        assertNull(StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":125,"color2":"plum","percent2":125}"""),
            null))
        assertNull(StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":9999,"color2":"plum","percent2":9999}"""),
            null))
    }

    @Test fun `a zero-sum mix is invalid`() {
        assertNull(StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"lch","color1":"purple","percent1":0,"color2":"plum","percent2":0}"""),
            null))
    }

    // ── Refusals ─────────────────────────────────────────────────────────

    @Test fun `an unreadable endpoint is refused rather than guessed`() {
        assertNull(StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"srgb","color1":"var(--x)","color2":"green"}"""),
            null))
    }

    @Test fun `an unsupported interpolation space is refused`() {
        // display-p3 is grammatical but outside GradientColorMath's table;
        // mixing in sRGB instead would paint a plausible wrong colour.
        assertNull(StaticColorMix.resolve(
            mix("""{"type":"color-mix","colorSpace":"display-p3","color1":"red","color2":"blue"}"""),
            null))
    }

    @Test fun `payload only claims color-mix values`() {
        assertNull(StaticColorMix.payload(null))
        assertNull(StaticColorMix.payload(wire("""{"original":"currentColor"}""")))
        assertNull(StaticColorMix.payload(wire("""{"original":{"type":"light-dark","lightColor":"red","darkColor":"blue"}}""")))
        assertNull(StaticColorMix.payload(wire("""{"srgb":{"r":1,"g":0,"b":0}}""")))
        assertNotNull(StaticColorMix.payload(
            wire("""{"original":{"type":"color-mix","colorSpace":"srgb","color1":"red","color2":"blue"}}""")))
    }

    // ── Agreement with the pre-existing srgb decoder ─────────────────────

    @Test fun `srgb mixes agree with the static decoder they used to take`() {
        // ValueExtractors.extractColor already resolved literal srgb mixes;
        // this path must reproduce it exactly or previously-correct
        // backgrounds would shift. color-mix(in srgb, red 50%, blue) is the
        // value that decoder's own doc cites: rgb(128,0,128).
        val payload = mix("""{"type":"color-mix","colorSpace":"srgb","color1":"red","percent1":50,"color2":"blue"}""")
        assertBytes(Triple(128, 0, 128), StaticColorMix.resolve(payload, null))
        val legacy = com.styleconverter.runtime.core.types.ValueExtractors.extractColor(
            Json.parseToJsonElement("""{"original":{"type":"color-mix","colorSpace":"srgb","color1":"red","percent1":50.0,"color2":"blue"}}"""))
        assertBytes(Triple(128, 0, 128), legacy)
    }
}
