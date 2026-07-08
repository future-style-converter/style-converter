package com.styleconverter.test.style.background

// IR shape tests for background-clip + background-origin array envelopes.
// Fixtures lifted from /tmp/c4-background-clip and /tmp/c4-background-origin.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundBoxExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test fun `BackgroundClip array of uppercase string BORDER_BOX`() {
        val cfg = BackgroundBoxExtractor.extractBackgroundBoxConfig(listOf(
            pair("BackgroundClip", "[\"BORDER_BOX\"]")
        ))
        assertEquals(BackgroundBoxValue.BORDER_BOX, cfg.backgroundClip)
    }

    @Test fun `BackgroundClip PADDING_BOX array`() {
        val cfg = BackgroundBoxExtractor.extractBackgroundBoxConfig(listOf(
            pair("BackgroundClip", "[\"PADDING_BOX\"]")
        ))
        assertEquals(BackgroundBoxValue.PADDING_BOX, cfg.backgroundClip)
    }

    @Test fun `BackgroundClip TEXT array`() {
        val cfg = BackgroundBoxExtractor.extractBackgroundBoxConfig(listOf(
            pair("BackgroundClip", "[\"TEXT\"]")
        ))
        assertEquals(BackgroundBoxValue.TEXT, cfg.backgroundClip)
    }

    @Test fun `BackgroundOrigin array of type-object padding-box`() {
        // Origin ships with a different shape than Clip — see CLAUDE.md phase 4.
        val cfg = BackgroundBoxExtractor.extractBackgroundBoxConfig(listOf(
            pair("BackgroundOrigin", "[{\"type\":\"padding-box\"}]")
        ))
        assertEquals(BackgroundBoxValue.PADDING_BOX, cfg.backgroundOrigin)
    }

    @Test fun `BackgroundOrigin content-box type-object`() {
        val cfg = BackgroundBoxExtractor.extractBackgroundBoxConfig(listOf(
            pair("BackgroundOrigin", "[{\"type\":\"content-box\"}]")
        ))
        assertEquals(BackgroundBoxValue.CONTENT_BOX, cfg.backgroundOrigin)
    }

    @Test fun `defaults when no properties present`() {
        val cfg = BackgroundBoxExtractor.extractBackgroundBoxConfig(emptyList())
        assertEquals(BackgroundBoxValue.BORDER_BOX, cfg.backgroundClip)
        assertEquals(BackgroundBoxValue.PADDING_BOX, cfg.backgroundOrigin)
    }
}
