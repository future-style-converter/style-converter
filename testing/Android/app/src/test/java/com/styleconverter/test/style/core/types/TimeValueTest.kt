package com.styleconverter.test.style.core.types

// Unit tests for extractTime and extractTimeList. Times are often wrapped
// in a list (TransitionDuration, AnimationDelay, …) — Quirk 7.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeValueTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test fun `seconds input normalized to ms with original preserved`() {
        val v = extractTime(parse("""{"ms":300.0,"original":{"v":0.3,"u":"S"}}"""))
        assertEquals(TimeValue(300.0), v)
    }

    @Test fun `milliseconds input has no original`() {
        val v = extractTime(parse("""{"ms":500.0}"""))
        assertEquals(TimeValue(500.0), v)
    }

    @Test fun `extractTimeList unwraps the TransitionDuration array shape`() {
        // Quirk 7: TransitionDuration data is an array of time objects.
        val list = extractTimeList(parse("""[{"ms":300.0,"original":{"v":0.3,"u":"S"}},{"ms":500.0}]"""))
        assertEquals(2, list.size)
        assertEquals(300.0, list[0].milliseconds, 1e-9)
        assertEquals(500.0, list[1].milliseconds, 1e-9)
    }

    @Test fun `extractTimeList empty on non-array`() {
        assertTrue(extractTimeList(parse("""{"ms":123}""")).isEmpty())
        assertTrue(extractTimeList(null).isEmpty())
    }

    @Test fun `missing ms returns null`() {
        assertNull(extractTime(parse("""{}""")))
        assertNull(extractTime(null))
    }
}
