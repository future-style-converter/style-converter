package com.styleconverter.test.style.spacing

// Unit tests for GapExtractor. Gap IR uses the tagged wrapper shape
// (type: "length"/"percentage"), unlike padding/margin which use raw shapes.

import com.styleconverter.test.style.core.types.LengthUnit
import com.styleconverter.test.style.core.types.LengthValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GapExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    @Test fun `row and column 10px each`() {
        val cfg = GapExtractor.extract(listOf(
            pair("RowGap", """{"type":"length","px":10.0}"""),
            pair("ColumnGap", """{"type":"length","px":10.0}"""),
        ))
        assertEquals(LengthValue.Exact(10.0), cfg.rowGap)
        assertEquals(LengthValue.Exact(10.0), cfg.columnGap)
    }

    @Test fun `asymmetric row vs column`() {
        val cfg = GapExtractor.extract(listOf(
            pair("RowGap", """{"type":"length","px":10.0}"""),
            pair("ColumnGap", """{"type":"length","px":40.0}"""),
        ))
        assertEquals(LengthValue.Exact(10.0), cfg.rowGap)
        assertEquals(LengthValue.Exact(40.0), cfg.columnGap)
    }

    @Test fun `row-only gap leaves column null`() {
        val cfg = GapExtractor.extract(listOf(
            pair("RowGap", """{"type":"length","px":10.0}"""),
        ))
        assertEquals(LengthValue.Exact(10.0), cfg.rowGap)
        assertNull(cfg.columnGap)
        assertTrue(cfg.hasGap)
    }

    @Test fun `percentage gap uses tagged percentage shape`() {
        val cfg = GapExtractor.extract(listOf(
            pair("RowGap", """{"type":"percentage","value":5.0}"""),
            pair("ColumnGap", """{"type":"percentage","value":5.0}"""),
        ))
        assertEquals(LengthValue.Relative(5.0, LengthUnit.PERCENT, null), cfg.rowGap)
    }

    @Test fun `em gap via type-length wrapper with original`() {
        val cfg = GapExtractor.extract(listOf(
            pair("RowGap", """{"type":"length","original":{"v":1.0,"u":"EM"}}"""),
        ))
        assertEquals(LengthValue.Relative(1.0, LengthUnit.EM, null), cfg.rowGap)
    }

    @Test fun `legacy Gap shorthand fills both axes`() {
        // Defensive: old IR might still emit "Gap" on some codepaths.
        val cfg = GapExtractor.extract(listOf(
            pair("Gap", """{"type":"length","px":20.0}"""),
        ))
        assertEquals(LengthValue.Exact(20.0), cfg.rowGap)
        assertEquals(LengthValue.Exact(20.0), cfg.columnGap)
    }

    @Test fun `empty list yields no gap`() {
        val cfg = GapExtractor.extract(emptyList())
        assertEquals(false, cfg.hasGap)
    }
}
