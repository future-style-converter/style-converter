package com.styleconverter.test.style.core.types

// Unit tests for extractAngle. IR always normalizes to degrees.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AngleValueTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test fun `plain degrees with no original`() {
        val v = extractAngle(parse("""{"deg":45.0}"""))
        assertEquals(AngleValue(45.0), v)
    }

    @Test fun `radians carry original but deg is canonical`() {
        val v = extractAngle(parse("""{"deg":45.00010522957486,"original":{"v":0.7854,"u":"RAD"}}"""))
        assertEquals(45.00010522957486, v!!.degrees, 1e-9)
    }

    @Test fun `turn normalized`() {
        val v = extractAngle(parse("""{"deg":180.0,"original":{"v":0.5,"u":"TURN"}}"""))
        assertEquals(180.0, v!!.degrees, 1e-9)
    }

    @Test fun `grad normalized`() {
        val v = extractAngle(parse("""{"deg":90.0,"original":{"v":100.0,"u":"GRAD"}}"""))
        assertEquals(90.0, v!!.degrees, 1e-9)
    }

    @Test fun `missing deg returns null`() {
        assertNull(extractAngle(parse("""{}""")))
        assertNull(extractAngle(null))
    }
}
