package com.styleconverter.runtime.color

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

    // ---- wave 9: per-layer comma lists (css-backgrounds-3 §2.3) -------------

    @Test fun `BackgroundSize two-layer list extracts both entries in order`() {
        // Verbatim `--to ir` for `background-size: 50px 25px, 30px 30px`.
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundSize",
                "[{\"w\":{\"px\":50.0},\"h\":{\"px\":25.0}},{\"w\":{\"px\":30.0},\"h\":{\"px\":30.0}}]")
        ))
        assertEquals(2, cfg.backgroundSizes.size)
        val first = cfg.backgroundSizes[0] as BackgroundSizeConfig.Dimensions
        assertEquals(50.dp, first.width)
        assertEquals(25.dp, first.height)
        val second = cfg.backgroundSizes[1] as BackgroundSizeConfig.Dimensions
        assertEquals(30.dp, second.width)
        // Legacy single field still carries the FIRST layer.
        assertEquals(first, cfg.backgroundSize)
    }

    @Test fun `BackgroundRepeat space round two-axis survives per-layer`() {
        // `background-repeat: space round` (Background_C03) — the flat enum
        // could never express this pair; the axes list can.
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundRepeat", "[{\"x\":\"space\",\"y\":\"round\"}]")
        ))
        assertEquals(1, cfg.backgroundRepeats.size)
        assertEquals(AxisRepeat.SPACE, cfg.backgroundRepeats[0].x)
        assertEquals(AxisRepeat.ROUND, cfg.backgroundRepeats[0].y)
    }

    @Test fun `BackgroundRepeat keyword list expands each layer per spec`() {
        // `background-repeat: repeat-x, no-repeat` → layer 0 (repeat,
        // no-repeat), layer 1 (no-repeat, no-repeat) — §3.7 expansion.
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundRepeat", "[\"repeat-x\",\"no-repeat\"]")
        ))
        assertEquals(2, cfg.backgroundRepeats.size)
        assertEquals(BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.NO_REPEAT), cfg.backgroundRepeats[0])
        assertEquals(BackgroundRepeatAxes(AxisRepeat.NO_REPEAT, AxisRepeat.NO_REPEAT), cfg.backgroundRepeats[1])
    }

    @Test fun `BackgroundPositionX length px lands as a Dp offset`() {
        // `background-position-x: 20px` ships {"type":"length","px":20} —
        // previously dropped; now a raw edge offset (fraction stays 0).
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundPositionX", "{\"type\":\"length\",\"px\":20.0}")
        ))
        assertEquals(0f, cfg.backgroundPosition.x)
        assertEquals(20.dp, cfg.backgroundPosition.xOffset)
    }

    @Test fun `layerValue cycles a short list and skips an empty one`() {
        // §2.3: "if a property has fewer values than background-image, the
        // list is repeated" — index 2 of a 2-list wraps to entry 0.
        val sizes = listOf<BackgroundSizeConfig>(
            BackgroundSizeConfig.Cover, BackgroundSizeConfig.Contain
        )
        assertEquals(BackgroundSizeConfig.Cover, ColorApplier.layerValue(sizes, 0))
        assertEquals(BackgroundSizeConfig.Contain, ColorApplier.layerValue(sizes, 1))
        assertEquals(BackgroundSizeConfig.Cover, ColorApplier.layerValue(sizes, 2))
        assertEquals(null, ColorApplier.layerValue(emptyList<BackgroundSizeConfig>(), 0))
    }
}
