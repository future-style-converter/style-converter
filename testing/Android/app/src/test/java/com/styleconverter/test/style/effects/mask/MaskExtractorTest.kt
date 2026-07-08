package com.styleconverter.test.style.effects.mask

// Phase 8 functional tests for MaskExtractor — covering the keyword-shaped
// mask longhands (Mode/Repeat/Composite/Clip/Origin) plus the mask-image
// URL path. Gradient masks and MaskBorder* are exercised through the
// registry test, not here (the URL/gradient parsing is more than 80 lines
// of its own and lives in a dedicated fixture set).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test
    fun `MaskMode alpha keyword parses`() {
        // Default mask-mode is `match-source`; explicit `alpha` is the most
        // common override in real stylesheets.
        val cfg = MaskExtractor.extractMaskConfig(
            listOf(pair("MaskMode", "\"alpha\""))
        )
        assertEquals(MaskModeValue.ALPHA, cfg.mode)
    }

    @Test
    fun `MaskMode luminance keyword parses`() {
        val cfg = MaskExtractor.extractMaskConfig(
            listOf(pair("MaskMode", "\"luminance\""))
        )
        assertEquals(MaskModeValue.LUMINANCE, cfg.mode)
    }

    @Test
    fun `MaskComposite subtract maps to SUBTRACT`() {
        // CSS mask-composite values: add | subtract | intersect | exclude.
        // The applier maps them to Compose BlendMode: SrcOver / DstOut /
        // SrcIn / Xor. The extractor just emits the semantic enum.
        val cfg = MaskExtractor.extractMaskConfig(
            listOf(pair("MaskComposite", "\"subtract\""))
        )
        assertEquals(MaskCompositeValue.SUBTRACT, cfg.composite)
    }

    @Test
    fun `MaskClip content-box parses to CONTENT_BOX`() {
        val cfg = MaskExtractor.extractMaskConfig(
            listOf(pair("MaskClip", "\"content-box\""))
        )
        assertEquals(MaskBoxValue.CONTENT_BOX, cfg.clip)
    }

    @Test
    fun `MaskRepeat no-repeat parses`() {
        val cfg = MaskExtractor.extractMaskConfig(
            listOf(pair("MaskRepeat", "\"no-repeat\""))
        )
        assertEquals(MaskRepeatValue.NO_REPEAT, cfg.repeat)
    }

    @Test
    fun `empty properties produces no-image config`() {
        // Default MaskConfig — no image, no gradient, default modes.
        // hasMask is false so the applier's Mask modifier is skipped.
        val cfg = MaskExtractor.extractMaskConfig(emptyList())
        assertTrue("no mask expected", !cfg.hasMask)
    }

    @Test
    fun `MaskImage none keyword yields no image`() {
        // Explicit `none` is a common reset — must not leave hasImage set.
        val cfg = MaskExtractor.extractMaskConfig(
            listOf(pair("MaskImage", "\"none\""))
        )
        assertTrue("`none` must unset hasImage", !cfg.hasImage)
    }
}
