package com.styleconverter.runtime.borders

// RC-B1 (wave 19, lane INK) — the border/outline currentColor bottom-out is
// MODE-SPLIT like text ink (corpus-v4.1 ink sub-boundary):
//
//   dark stage  (wptCaptureMode=false) → #EEEEEE, the harness body color the
//     web reference resolves currentcolor to (`body { color:#eee }`) — the
//     327 committed baselines pin this side and it must never move;
//   WPT capture (wptCaptureMode=true)  → spec BLACK, the UA `color:
//     CanvasText` a real WPT page's inherit chain ends at (html.css) — the
//     browser-ref paints `border: 1px solid` (color omitted → currentcolor,
//     css-color-4 §7.1) BLACK, so the natives must too. css-break
//     background-image-000's entire 0.874 penalty was this bottom-out
//     painting rgb(238,238,238) frames against the ref's black.
//
// The Swift twin is BorderSideApplier.fallbackInk / OutlineApplier reusing
// it (WPTCaptureModeTests pins that side); both natives route through the
// SAME pure ink split (Compose defaultTextInk / iOS WPTCanvas.captureTextInk)
// so the mode boundary can never drift between platforms.

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.borders.outline.OutlineExtractor
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.core.renderer.WPT_DEFAULT_TEXT_INK
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class WptBorderInkTest {

    // Helper: parse a JSON string into a JsonElement (BorderExtractorsTest
    // pattern — the shapes below are byte-copies of the LIVE wave18 wire).
    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // The LIVE css-break background-image-000 border wire — `border: 1px
    // solid` with NO Border*Color anywhere (tools/titan/runs/wave18-final/
    // sections/css-break/per-test-ir/wpt__css-break__background-image-000.json).
    private val colourlessBorder = listOf(
        pair("BorderTopWidth", """{"px":1}"""),
        pair("BorderTopStyle", "\"SOLID\""),
        pair("BorderRightWidth", """{"px":1}"""),
        pair("BorderRightStyle", "\"SOLID\""),
    )

    // The dark-stage harness ink — the historical bottom-out.
    private val eee = Color(0xFFEEEEEE)

    // ── border sides ─────────────────────────────────────────────────────

    @Test
    fun `dark stage keeps the harness eee bottom-out byte-identically`() {
        // Flag omitted (the default) — every legacy width-only call site
        // AND the whole 327-pair corpus stay on this side.
        val cfg = BorderSideExtractor.extractBorderConfig(colourlessBorder)
        assertEquals(eee, cfg.top.color)
        // Explicit false is the same side (no tri-state here).
        val explicit = BorderSideExtractor.extractBorderConfig(colourlessBorder, wptCaptureMode = false)
        assertEquals(eee, explicit.end.color)
    }

    @Test
    fun `wpt capture bottoms colourless border sides out at spec black`() {
        // css-break-000's fix: the same wire under the WPT flag paints the
        // corpus-v4.1 spec black — the ref's UA CanvasText ink.
        val cfg = BorderSideExtractor.extractBorderConfig(colourlessBorder, wptCaptureMode = true)
        assertEquals(WPT_DEFAULT_TEXT_INK, cfg.top.color)
        assertEquals(WPT_DEFAULT_TEXT_INK, cfg.end.color)
    }

    @Test
    fun `element colour still wins over the wpt black bottom-out`() {
        // currentColor resolves to the element's own `color` FIRST in both
        // modes (css-backgrounds-3 §4.1 initial = currentcolor) — the mode
        // split only governs the no-Color-anywhere bottom of the chain.
        val teal = pair("Color", """{"srgb":{"r":0.0,"g":0.5,"b":0.5}}""")
        val cfg = BorderSideExtractor.extractBorderConfig(colourlessBorder + teal, wptCaptureMode = true)
        assertEquals(Color(0f, 0.5f, 0.5f), cfg.top.color)
    }

    @Test
    fun `explicit border colour is untouched by the mode split`() {
        // block-ellipsis-001 ships explicit black Border*Color — declared
        // colours bypass the bottom-out entirely on both sides of the flag.
        val declared = colourlessBorder +
            pair("BorderTopColor", """{"srgb":{"r":0,"g":0,"b":0},"original":"black"}""")
        val dark = BorderSideExtractor.extractBorderConfig(declared)
        val wpt = BorderSideExtractor.extractBorderConfig(declared, wptCaptureMode = true)
        assertEquals(Color(0f, 0f, 0f), dark.top.color)
        assertEquals(dark.top.color, wpt.top.color)
    }

    // ── outline ──────────────────────────────────────────────────────────

    @Test
    fun `outline currentColor bottom-out mirrors the same mode split`() {
        // css-ui-4 §3.4: outline-color's initial is currentColor — same
        // chain, same split (the OutlineApplier's ring must not stay #eee
        // on the white WPT canvas while the ref draws black).
        val outline = listOf(
            pair("OutlineWidth", """{"px":2.0}"""),
            pair("OutlineStyle", "\"solid\""),
        )
        assertEquals(eee, OutlineExtractor.extractOutlineConfig(outline).color)
        assertEquals(
            WPT_DEFAULT_TEXT_INK,
            OutlineExtractor.extractOutlineConfig(outline, wptCaptureMode = true).color
        )
    }

    @Test
    fun `outline element colour wins in wpt mode too`() {
        // Same precedence pin as the border sides: element `color` beats
        // the mode-split bottom-out.
        val props = listOf(
            pair("OutlineWidth", """{"px":2.0}"""),
            pair("OutlineStyle", "\"solid\""),
            pair("Color", """{"srgb":{"r":1.0,"g":0.0,"b":0.0}}"""),
        )
        assertEquals(
            Color(1f, 0f, 0f),
            OutlineExtractor.extractOutlineConfig(props, wptCaptureMode = true).color
        )
    }
}
