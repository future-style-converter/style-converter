package com.styleconverter.test.style.color

// IR shape tests for the Phase 4 background array-envelope fix.
// Fixtures pulled from /tmp/c4-background-size, /tmp/c4-background-repeat,
// /tmp/c4-background-position, /tmp/c4-background-attachment.

import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // ---- BackgroundSize ----------------------------------------------------

    @Test fun `BackgroundSize array cover`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundSize", "[\"cover\"]")
        ))
        assertEquals(BackgroundSizeConfig.Cover, cfg.backgroundSize)
    }

    @Test fun `BackgroundSize array contain`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundSize", "[\"contain\"]")
        ))
        assertEquals(BackgroundSizeConfig.Contain, cfg.backgroundSize)
    }

    @Test fun `BackgroundSize array of px width parses to Dimensions`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundSize", "[{\"w\":{\"px\":100.0}}]")
        ))
        val dim = cfg.backgroundSize as BackgroundSizeConfig.Dimensions
        assertEquals(100.dp, dim.width)
    }

    @Test fun `BackgroundSize array of w-and-h px parses both`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundSize", "[{\"w\":{\"px\":40.0},\"h\":{\"px\":40.0}}]")
        ))
        val dim = cfg.backgroundSize as BackgroundSizeConfig.Dimensions
        assertEquals(40.dp, dim.width)
        assertEquals(40.dp, dim.height)
    }

    @Test fun `BackgroundSize bare-number means percentage`() {
        // {"w":50.0} in IR means 50% — covered in the Phase 4 IR notes.
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundSize", "[{\"w\":50.0}]")
        ))
        val dim = cfg.backgroundSize as BackgroundSizeConfig.Dimensions
        assertEquals(0.5f, dim.widthPercent)
    }

    // ---- BackgroundRepeat --------------------------------------------------

    @Test fun `BackgroundRepeat repeat-x two-axis object`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundRepeat", "[{\"x\":\"repeat\",\"y\":\"no-repeat\"}]")
        ))
        assertEquals(BackgroundRepeatConfig.REPEAT_X, cfg.backgroundRepeat)
    }

    @Test fun `BackgroundRepeat repeat-y two-axis object`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundRepeat", "[{\"x\":\"no-repeat\",\"y\":\"repeat\"}]")
        ))
        assertEquals(BackgroundRepeatConfig.REPEAT_Y, cfg.backgroundRepeat)
    }

    @Test fun `BackgroundRepeat array string no-repeat`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundRepeat", "[\"no-repeat\"]")
        ))
        assertEquals(BackgroundRepeatConfig.NO_REPEAT, cfg.backgroundRepeat)
    }

    // ---- BackgroundPositionX / Y -------------------------------------------

    @Test fun `BackgroundPositionX LEFT keyword lands as 0`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundPositionX", "{\"type\":\"keyword\",\"value\":\"LEFT\"}")
        ))
        assertEquals(0f, cfg.backgroundPosition.x)
    }

    @Test fun `BackgroundPositionY CENTER lands as 0_5`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundPositionY", "{\"type\":\"keyword\",\"value\":\"CENTER\"}")
        ))
        assertEquals(0.5f, cfg.backgroundPosition.y)
    }

    @Test fun `BackgroundPositionX percentage 75 lands as 0_75`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundPositionX", "{\"type\":\"percentage\",\"percentage\":75.0}")
        ))
        assertEquals(0.75f, cfg.backgroundPosition.x)
    }

    // ---- BackgroundAttachment ----------------------------------------------

    @Test fun `BackgroundAttachment fixed in object array`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundAttachment", "[{\"type\":\"fixed\"}]")
        ))
        assertEquals(BackgroundAttachment.FIXED, cfg.backgroundAttachment)
    }

    @Test fun `BackgroundAttachment scroll default`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundAttachment", "[{\"type\":\"scroll\"}]")
        ))
        assertEquals(BackgroundAttachment.SCROLL, cfg.backgroundAttachment)
    }

    // ---- Opacity -----------------------------------------------------------

    @Test fun `Opacity with alpha envelope parses direct alpha`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Opacity", "{\"alpha\":0.5,\"original\":{\"type\":\"number\",\"value\":0.5}}")
        ))
        assertEquals(0.5f, cfg.opacity)
    }
}
