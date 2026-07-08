package com.styleconverter.test.style.spacing

// Unit tests for MarginTrimExtractor. Fixture: margin-trim.json, 7 keywords.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginTrimExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(json: String) = listOf("MarginTrim" to parse(json))

    @Test fun `none is the default initial value`() {
        val cfg = MarginTrimExtractor.extract(emptyList())
        assertEquals(MarginTrimKeyword.NONE, cfg.value)
        assertEquals(false, cfg.hasMarginTrim)
    }

    @Test fun `each SCREAMING_SNAKE keyword maps to its enum variant`() {
        val expected = mapOf(
            "NONE" to MarginTrimKeyword.NONE,
            "BLOCK" to MarginTrimKeyword.BLOCK,
            "INLINE" to MarginTrimKeyword.INLINE,
            "BLOCK_START" to MarginTrimKeyword.BLOCK_START,
            "BLOCK_END" to MarginTrimKeyword.BLOCK_END,
            "INLINE_START" to MarginTrimKeyword.INLINE_START,
            "INLINE_END" to MarginTrimKeyword.INLINE_END,
        )
        for ((kw, want) in expected) {
            val cfg = MarginTrimExtractor.extract(pair(""""$kw""""))
            assertEquals(kw, want, cfg.value)
        }
    }

    @Test fun `hyphenated lowercase accepted as fallback`() {
        val cfg = MarginTrimExtractor.extract(pair(""""block-start""""))
        assertEquals(MarginTrimKeyword.BLOCK_START, cfg.value)
    }

    @Test fun `block keyword means something active`() {
        val cfg = MarginTrimExtractor.extract(pair(""""BLOCK""""))
        assertTrue(cfg.hasMarginTrim)
    }
}
