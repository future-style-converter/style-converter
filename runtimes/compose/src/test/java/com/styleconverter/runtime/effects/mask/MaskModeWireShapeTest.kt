package com.styleconverter.runtime.effects.mask

// Fidelity wave 1 — pinning test for the MaskMode discriminator wire shape.
//
// The converter serializes `mask-mode` as a sealed-class discriminator
// OBJECT — {"type":"app.irmodels.properties.effects.MaskModeValue.Luminance"}
// — NOT a keyword string. The old extractor only understood keywords, so
// `mask-mode: luminance` silently fell back to MATCH_SOURCE and a black
// gradient (luminance 0 everywhere) rendered as an alpha fade instead of
// fully masking the box out (Effects_C05_MaskImage: web/iOS blank, Android
// showed a left-to-right fade — Android-web 0.8527).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MaskModeWireShapeTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // Minimal gradient layer so hasMask is true and the mode is meaningful.
    private val gradientJson =
        """[{"type":"linear-gradient","angle":{"deg":90.0},"stops":[{"color":{"srgb":{"r":0.0,"g":0.0,"b":0.0}},"position":0.0},{"color":{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.0}},"position":100.0}]}]"""

    @Test
    fun `luminance discriminator object parses to LUMINANCE`() {
        val cfg = MaskExtractor.extractMaskConfig(listOf(
            pair("MaskImage", gradientJson),
            pair("MaskMode", """{"type":"app.irmodels.properties.effects.MaskModeValue.Luminance"}""")
        ))
        assertEquals(MaskModeValue.LUMINANCE, cfg.mode)
    }

    @Test
    fun `alpha discriminator object parses to ALPHA`() {
        val cfg = MaskExtractor.extractMaskConfig(listOf(
            pair("MaskImage", gradientJson),
            pair("MaskMode", """{"type":"app.irmodels.properties.effects.MaskModeValue.Alpha"}""")
        ))
        assertEquals(MaskModeValue.ALPHA, cfg.mode)
    }

    @Test
    fun `legacy keyword string still parses`() {
        // Older hand-written fixtures / tests use plain keyword strings —
        // the fallback path must keep accepting them.
        val cfg = MaskExtractor.extractMaskConfig(listOf(
            pair("MaskImage", gradientJson),
            pair("MaskMode", "\"LUMINANCE\"")
        ))
        assertEquals(MaskModeValue.LUMINANCE, cfg.mode)
    }

    @Test
    fun `absent mask-mode stays MATCH_SOURCE`() {
        val cfg = MaskExtractor.extractMaskConfig(listOf(
            pair("MaskImage", gradientJson)
        ))
        assertEquals(MaskModeValue.MATCH_SOURCE, cfg.mode)
    }
}
