package com.styleconverter.test.style.core.types

// Tests for KeywordValue normalization + .matches() cross-convention check.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordValueTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test fun `bare string gets canonicalized to lowercase hyphen`() {
        val v = extractKeyword(parse(""""flex-start""""))!!
        assertEquals("flex-start", v.normalized)
    }

    @Test fun `legacy uppercase underscore normalized to CSS form`() {
        val v = extractKeyword(parse(""""FLEX_START""""))!!
        assertEquals("flex-start", v.normalized)
    }

    @Test fun `matches accepts both conventions`() {
        val v = extractKeyword(parse(""""flex-start""""))!!
        assertTrue(v.matches("FLEX_START"))
        assertTrue(v.matches("flex-start"))
        assertTrue(v.matches("  flex-start  "))
        assertFalse(v.matches("center"))
    }

    @Test fun `object with type tag`() {
        val v = extractKeyword(parse("""{"type":"flex-start"}"""))!!
        assertEquals("flex-start", v.normalized)
    }

    @Test fun `object with keyword field`() {
        val v = extractKeyword(parse("""{"keyword":"solid"}"""))!!
        assertEquals("solid", v.normalized)
    }

    @Test fun `object with value field`() {
        val v = extractKeyword(parse("""{"value":"HIDDEN"}"""))!!
        assertEquals("hidden", v.normalized)
    }

    @Test fun `non-string values return null`() {
        assertNull(extractKeyword(null))
        assertNull(extractKeyword(parse("""{}""")))
        assertNull(extractKeyword(parse("""42""")))
        assertNull(extractKeyword(parse("""{"value":10}""")))
    }
}
