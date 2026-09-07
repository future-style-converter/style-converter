package com.styleconverter.runtime.effects.filter

// Retro R6 (audit finding A7#1) — the drop-shadow() default colour.
// filter-effects-1 §6.1: "the missing used color is taken from the color
// property"; an explicit `currentColor` resolves to the same (css-color-4
// §6.4). The wire for both is `c: {"original": "currentColor"}` with NO srgb
// block — ValueExtractors.extractColor returns null — and the extractor
// substituted OPAQUE BLACK (Swift: black at 30 % alpha; web resolves the real
// colour): three renders for one wire. Corpus carrier css-color/
// currentcolor-003 painted a solid red-on-red block on Android where the ref
// paints copies in the text colour.
//
// The payloads below are VERBATIM from
// tools/titan/runs/wave49-final/sections/css-color/per-test-ir/
// wpt__css-color__currentcolor-003.json (component currentcolor-003__1__0).
// Proven able to fail: restoring `?: Color.Black` fails the first three tests.

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterExtractorDropShadowInkTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // currentcolor-003__1__0's Filter, byte for byte.
    private val currentColorWire =
        """[{"fn": "drop-shadow", "x": {"px": 333}, "y": {"px": 0}, "r": {"px": 0}, "c": {"original": "currentColor"}}]"""

    // The element's `color` as the corpus emits it (CSS `green`).
    private val greenColor = """{"srgb": {"r": 0, "g": 0.5019607843137255, "b": 0}, "original": "green"}"""

    private fun dropShadow(cfg: FilterConfig): FilterFunction.DropShadow {
        assertEquals(1, cfg.filters.size)
        return cfg.filters.single() as FilterFunction.DropShadow
    }

    // 8-bit channel readback (Compose packs sRGB Colors to ARGB8888).
    private fun channels(c: Color) = Triple(
        Math.round(c.red * 255f), Math.round(c.green * 255f), Math.round(c.blue * 255f)
    )

    @Test
    fun `currentColor with no element color bottoms out at the dark-stage ink`() {
        // No `Color` property on the element, dark stage (wptCaptureMode
        // false): the web harness body `color: #eee` — the same bottom-out
        // BorderSideExtractor uses for border-color's currentcolor initial.
        val cfg = FilterExtractor.extractFilterConfig(listOf(pair("Filter", currentColorWire)))
        val ds = dropShadow(cfg)
        assertEquals(Triple(238, 238, 238), channels(ds.color))
        assertEquals("alpha must be 1 — not Swift's old 0.3", 1f, ds.color.alpha, 0f)
    }

    @Test
    fun `currentColor with no element color is spec black under WPT capture`() {
        // A real WPT page's inherit chain ends at the UA `color: CanvasText`
        // black (RC-B1 mode split) — WptCaptureMode.WPT_DEFAULT_TEXT_INK.
        val cfg = FilterExtractor.extractFilterConfig(listOf(pair("Filter", currentColorWire)), wptCaptureMode = true)
        assertEquals(Triple(0, 0, 0), channels(dropShadow(cfg).color))
        assertEquals(1f, dropShadow(cfg).color.alpha, 0f)
    }

    @Test
    fun `currentColor takes the element's own color when declared`() {
        // The §6.1 rule proper: the element's `color` wins over any default,
        // in either capture mode.
        for (wpt in listOf(false, true)) {
            val cfg = FilterExtractor.extractFilterConfig(
                listOf(pair("Color", greenColor), pair("Filter", currentColorWire)), wptCaptureMode = wpt,
            )
            assertEquals("wpt=$wpt", Triple(0, 128, 0), channels(dropShadow(cfg).color))
        }
    }

    @Test
    fun `an explicit srgb colour is untouched`() {
        // clip-path-filter-order's wire (explicit red) must keep its colour —
        // the default only fills the unresolved case.
        val red = """[{"fn": "drop-shadow", "x": {"px": 16}, "y": {"px": 16}, "r": {"px": 20}, "c": {"srgb": {"r": 1, "g": 0, "b": 0}, "original": "red"}}]"""
        val cfg = FilterExtractor.extractFilterConfig(listOf(pair("Color", greenColor), pair("Filter", red)))
        assertEquals(Triple(255, 0, 0), channels(dropShadow(cfg).color))
    }

    @Test
    fun `geometry of the verbatim wire survives the colour threading`() {
        // x=333 / y=0 / r=0 — the offsets that place the copies at +333px.
        val ds = dropShadow(FilterExtractor.extractFilterConfig(listOf(pair("Filter", currentColorWire))))
        assertEquals(333f, ds.offsetX.value, 0f)
        assertEquals(0f, ds.offsetY.value, 0f)
        assertEquals(0f, ds.blurRadius.value, 0f)
        assertTrue(ds.color.alpha == 1f)
    }
}
