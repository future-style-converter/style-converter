package com.styleconverter.runtime.effects.shadow

// Retro R6 — the box-shadow sibling of the drop-shadow default-colour fix
// (A7#1). css-backgrounds-3 §6.1: "If the color is absent, the used color is
// taken from the color property"; `currentColor` resolves to the same. The
// wire `c: {"original": "currentColor"}` decodes to null in
// ValueExtractors.extractColor, and ValueExtractors.extractShadows then
// substituted OPAQUE BLACK. The payload below is VERBATIM from
// tools/titan/runs/wave49-final/sections/css-color/per-test-ir/
// wpt__css-color__currentcolor-003.json (component currentcolor-003__1__0).
// Proven able to fail: dropping the `declared ?: currentColorInk` override
// (using ValueExtractors' colour as before) fails the first three tests.

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ShadowExtractorCurrentColorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // currentcolor-003__1__0's BoxShadow, byte for byte.
    private val wire = """[{"x": {"px": 111}, "y": {"px": 0}, "blur": {"px": 5}, "c": {"original": "currentColor"}}]"""
    private val greenColor = """{"srgb": {"r": 0, "g": 0.5019607843137255, "b": 0}, "original": "green"}"""

    private fun channels(c: Color) = Triple(
        Math.round(c.red * 255f), Math.round(c.green * 255f), Math.round(c.blue * 255f)
    )

    private fun only(cfg: ShadowConfig): ShadowData {
        assertEquals(1, cfg.shadows.size)
        return cfg.shadows.single()
    }

    @Test
    fun `currentColor shadow takes the element's color`() {
        val cfg = ShadowExtractor.extractShadowConfig(listOf(pair("Color", greenColor), pair("BoxShadow", wire)))
        assertEquals(Triple(0, 128, 0), channels(only(cfg).color))
    }

    @Test
    fun `currentColor shadow bottoms out at the dark-stage ink without an element color`() {
        // #eee — the web harness body colour, BorderSideExtractor's bottom-out.
        val cfg = ShadowExtractor.extractShadowConfig(listOf(pair("BoxShadow", wire)))
        assertEquals(Triple(238, 238, 238), channels(only(cfg).color))
    }

    @Test
    fun `currentColor shadow is spec black under WPT capture`() {
        val cfg = ShadowExtractor.extractShadowConfig(listOf(pair("BoxShadow", wire)), wptCaptureMode = true)
        assertEquals(Triple(0, 0, 0), channels(only(cfg).color))
    }

    @Test
    fun `an explicit colour and the geometry are untouched`() {
        // backdrop-filter-box-shadow's wire (explicit #888888) — the default
        // only fills the unresolved case; offsets/blur/spread pass through.
        val grey = """[{"x": {"px": 20}, "y": {"px": 20}, "blur": {"px": 10}, "spread": {"px": 0}, "c": {"srgb": {"r": 0.5333333333333333, "g": 0.5333333333333333, "b": 0.5333333333333333}, "original": "#888888"}}]"""
        val s = only(ShadowExtractor.extractShadowConfig(listOf(pair("Color", greenColor), pair("BoxShadow", grey))))
        assertEquals(Triple(136, 136, 136), channels(s.color))
        assertEquals(20f, s.offsetX.value, 0f)
        assertEquals(20f, s.offsetY.value, 0f)
        assertEquals(10f, s.blurRadius.value, 0f)
        // And the verbatim currentColor wire keeps ITS geometry too.
        val c = only(ShadowExtractor.extractShadowConfig(listOf(pair("BoxShadow", wire))))
        assertEquals(111f, c.offsetX.value, 0f)
        assertEquals(5f, c.blurRadius.value, 0f)
    }

    @Test
    fun `explicit black stays black - the override only fills null`() {
        // A declared opaque black must not be confused with "unresolved".
        val black = """[{"x": {"px": 1}, "y": {"px": 1}, "c": {"srgb": {"r": 0, "g": 0, "b": 0}, "original": "black"}}]"""
        val s = only(ShadowExtractor.extractShadowConfig(listOf(pair("Color", greenColor), pair("BoxShadow", black))))
        assertEquals(Triple(0, 0, 0), channels(s.color))
    }
}
