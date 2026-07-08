package com.styleconverter.test.style.effects.blend

// Fixture shapes lifted from /tmp/c4-blend-modes/tmpOutput.json.

import androidx.compose.ui.graphics.BlendMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendModeExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test fun `MixBlendMode MULTIPLY parses to Multiply`() {
        val cfg = BlendModeExtractor.extractBlendModeConfig(listOf(
            pair("MixBlendMode", "\"MULTIPLY\"")
        ))
        assertEquals(BlendMode.Multiply, cfg.blendMode)
        assertTrue(cfg.hasBlendMode)
    }

    @Test fun `MixBlendMode NORMAL maps to SrcOver and hasBlendMode stays false`() {
        // SrcOver is the default — treat as "not a real override" so the
        // applier can skip the graphicsLayer allocation.
        val cfg = BlendModeExtractor.extractBlendModeConfig(listOf(
            pair("MixBlendMode", "\"NORMAL\"")
        ))
        assertEquals(BlendMode.SrcOver, cfg.blendMode)
        assertFalse(cfg.hasBlendMode)
    }

    @Test fun `MixBlendMode PLUS_LIGHTER maps to Plus`() {
        val cfg = BlendModeExtractor.extractBlendModeConfig(listOf(
            pair("MixBlendMode", "\"PLUS_LIGHTER\"")
        ))
        assertEquals(BlendMode.Plus, cfg.blendMode)
    }

    @Test fun `BackgroundBlendMode list of two parses both`() {
        val cfg = BlendModeExtractor.extractBlendModeConfig(listOf(
            pair("BackgroundBlendMode", "[\"MULTIPLY\",\"SCREEN\"]")
        ))
        assertEquals(listOf(BlendMode.Multiply, BlendMode.Screen), cfg.backgroundBlendModes)
        assertTrue(cfg.hasBackgroundBlendMode)
    }

    @Test fun `unknown string is filtered out silently`() {
        // Unknown tokens drop out of the resolved list rather than producing
        // a null sentinel — callers that iterate the list expect real modes.
        val cfg = BlendModeExtractor.extractBlendModeConfig(listOf(
            pair("BackgroundBlendMode", "[\"MULTIPLY\",\"NOT_A_MODE\"]")
        ))
        assertEquals(listOf(BlendMode.Multiply), cfg.backgroundBlendModes)
    }
}
