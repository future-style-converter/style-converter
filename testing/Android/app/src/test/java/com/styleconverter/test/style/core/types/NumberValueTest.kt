package com.styleconverter.test.style.core.types

// Unit tests for the property-adapter number helpers. Quirk 8: no single
// IRNumber exists — every property has its own envelope.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberValueTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test fun `opacity reads alpha key`() {
        val v = extractOpacity(parse("""{"alpha":0.5,"original":{"type":"number","value":0.5}}"""))
        assertEquals(0.5, v!!.value, 1e-9)
    }

    @Test fun `line-height multiplier`() {
        val v = extractLineHeightMultiplier(parse("""{"multiplier":1.5,"original":{"type":"number","value":1.5}}"""))
        assertEquals(1.5, v!!.value, 1e-9)
    }

    @Test fun `flex-grow normalizedValue`() {
        val v = extractFlexGrow(parse(
            """{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexGrowProperty.FlexGrowValue.Number","value":1.0},"normalizedValue":1.0}"""
        ))
        assertEquals(1.0, v!!.value, 1e-9)
    }

    @Test fun `z-index reads outer value`() {
        val v = extractZIndex(parse("""{"value":10,"original":{"type":"integer","value":10}}"""))
        assertEquals(10.0, v!!.value, 1e-9)
    }

    @Test fun `font-weight is a bare integer primitive`() {
        val v = extractFontWeight(parse("""700"""))
        assertEquals(700.0, v!!.value, 1e-9)
    }

    @Test fun `font-size exposes px scalar`() {
        val v = extractFontSizePx(parse("""{"px":16,"original":{"type":"length","px":16}}"""))
        assertEquals(16.0, v!!.value, 1e-9)
    }

    @Test fun `all adapters return null on malformed input`() {
        assertNull(extractOpacity(parse("""{}""")))
        assertNull(extractLineHeightMultiplier(null))
        assertNull(extractFlexGrow(parse("""{}""")))
        assertNull(extractZIndex(parse("""{}""")))
        assertNull(extractFontWeight(parse("""{}""")))
        assertNull(extractFontSizePx(parse("""{}""")))
    }
}
