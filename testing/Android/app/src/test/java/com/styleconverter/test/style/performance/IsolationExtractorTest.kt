package com.styleconverter.test.style.performance

// Fixture shapes lifted from /tmp/c4-isolation/tmpOutput.json.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolationExtractorTest {

    // Convenience — keep the tests terse like the sizing tests.
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test fun `default is AUTO when no property present`() {
        val cfg = IsolationExtractor.extractIsolationConfig(emptyList())
        assertEquals(IsolationConfig.Value.AUTO, cfg.value)
        assertFalse(cfg.hasIsolation)
    }

    @Test fun `ISOLATE primitive string lands as ISOLATE`() {
        // Bare JSON string — the shape the IR actually emits today.
        val cfg = IsolationExtractor.extractIsolationConfig(listOf(
            pair("Isolation", "\"ISOLATE\"")
        ))
        assertEquals(IsolationConfig.Value.ISOLATE, cfg.value)
        assertTrue(cfg.hasIsolation)
    }

    @Test fun `AUTO primitive string is AUTO`() {
        val cfg = IsolationExtractor.extractIsolationConfig(listOf(
            pair("Isolation", "\"AUTO\"")
        ))
        assertEquals(IsolationConfig.Value.AUTO, cfg.value)
    }

    @Test fun `object shape with type is also supported`() {
        // Defensive path — no fixture emits this today but we keep it
        // tolerant of parser drift.
        val cfg = IsolationExtractor.extractIsolationConfig(listOf(
            pair("Isolation", """{"type":"isolate"}""")
        ))
        assertEquals(IsolationConfig.Value.ISOLATE, cfg.value)
    }

    @Test fun `unknown keyword falls back to AUTO`() {
        val cfg = IsolationExtractor.extractIsolationConfig(listOf(
            pair("Isolation", "\"NOT_A_THING\"")
        ))
        assertEquals(IsolationConfig.Value.AUTO, cfg.value)
    }
}
