package com.styleconverter.runtime.color

// Retro R6 (audit finding A11#10, background-clip:text half) — a SOLID
// background under `background-clip: text`. css-backgrounds-4 §2.8 (Painting
// Area — `text` is a Level 4 value; Level 3 §2.7 is <visual-box> only) confines
// every background layer, the solid colour included, to the glyph mask; the
// box itself never gets painted. Web (glyph-masked) and iOS (nothing) leave
// pairwise pairs-01 008 PW_Background_Effects_01 empty; Android flooded the
// full 140×96 box with #3498db because only the IMAGE layers were suppressed.
// The payloads are VERBATIM from the A11 audit run's ir.json for
// fixtures/fidelity/pairwise/pairs-01.json (component 008).
// Proven able to fail: removing the post-loop `backgroundColor = null` block
// in ColorExtractor fails the first test.

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorExtractorBackgroundClipTextTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // PW_Background_Effects_01, byte for byte.
    private val clipText = """["TEXT"]"""
    private val blue = """{"srgb": {"r": 0.20392156862745098, "g": 0.596078431372549, "b": 0.8588235294117647}, "original": "#3498db"}"""
    private val size = listOf(
        pair("Width", """{"type": "length", "px": 140.0}"""),
        pair("Height", """{"type": "length", "px": 96.0}"""),
    )

    @Test
    fun `clip text drops the solid background from the box paint`() {
        val cfg = ColorExtractor.extractColorConfig(
            size + listOf(pair("BackgroundClip", clipText), pair("BackgroundColor", blue))
        )
        assertTrue("image layers are suppressed as before", cfg.suppressBackgroundImage)
        assertNull("§2.7: the solid colour is confined to the glyphs — no box fill", cfg.backgroundColor)
    }

    @Test
    fun `property order does not matter`() {
        // BackgroundColor first, then the clip — the decision is post-loop.
        val cfg = ColorExtractor.extractColorConfig(
            listOf(pair("BackgroundColor", blue), pair("BackgroundClip", clipText)) + size
        )
        assertNull(cfg.backgroundColor)
    }

    @Test
    fun `any other clip keyword keeps the solid background`() {
        // border-box (initial) and padding-box paint the box as before —
        // only `text` moves the painting area to the glyphs.
        for (clip in listOf("""["BORDER_BOX"]""", """["PADDING_BOX"]""")) {
            val cfg = ColorExtractor.extractColorConfig(
                size + listOf(pair("BackgroundClip", clip), pair("BackgroundColor", blue))
            )
            assertEquals(clip, Color(0.20392156862745098f, 0.596078431372549f, 0.8588235294117647f), cfg.backgroundColor)
        }
    }

    @Test
    fun `no clip property keeps the solid background`() {
        val cfg = ColorExtractor.extractColorConfig(size + listOf(pair("BackgroundColor", blue)))
        assertEquals(Color(0.20392156862745098f, 0.596078431372549f, 0.8588235294117647f), cfg.backgroundColor)
        assertTrue(!cfg.suppressBackgroundImage)
    }
}
