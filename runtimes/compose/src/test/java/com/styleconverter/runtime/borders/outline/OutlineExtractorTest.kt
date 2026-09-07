package com.styleconverter.runtime.borders.outline

// Wave-5 pinning — outline-color's INITIAL value is `currentColor`
// (css-ui-4 §3.4), NOT black. The web reference harness sets
// `body { color: #eee }` (apps/web-harness/index.html), so a fixture that
// declares only `outline-style: groove` paints light-gray 3D bands on web;
// Android's old Color.Black default painted a solid black frame on every
// such fixture (PW_Borders_Sizing_04 A-w 0.657, outline-style lift 5/5).

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OutlineExtractorTest {

    private fun pair(type: String, json: String) =
        type to Json.parseToJsonElement(json)

    @Test
    fun `outline color defaults to the harness currentColor not black`() {
        // No outline-color, no element color → the web body's #eee.
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineStyle", "\"GROOVE\"")
        ))
        assertEquals(OutlineStyle.GROOVE, cfg.style)
        assertEquals(Color(0xFFEEEEEE), cfg.color)
    }

    @Test
    fun `element color property feeds currentColor`() {
        // `color: #f39` on the element → outline paints in it (css-ui-4
        // §4.3 currentColor resolution; inheritance is folded upstream by
        // ComponentRenderer.mergeInherited).
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineStyle", "\"SOLID\""),
            pair("Color", """{"srgb":{"r":1.0,"g":0.2,"b":0.6}}""")
        ))
        assertEquals(1f, cfg.color.red, 0.01f)
        assertEquals(0.2f, cfg.color.green, 0.01f)
        assertEquals(0.6f, cfg.color.blue, 0.01f)
    }

    @Test
    fun `explicit outline color still wins`() {
        // Explicit `outline-color: crimson` overrides currentColor — the
        // Borders_C14 / Outline_Solid committed-baseline behaviour.
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineStyle", "\"SOLID\""),
            pair("Color", """{"srgb":{"r":1.0,"g":0.2,"b":0.6}}"""),
            pair("OutlineColor", """{"srgb":{"r":0.863,"g":0.078,"b":0.235}}""")
        ))
        assertEquals(0.863f, cfg.color.red, 0.01f)
        assertEquals(0.078f, cfg.color.green, 0.01f)
    }
}
