package com.styleconverter.test.style.spacing

// Unit tests for MarginExtractor. Emphasis on the auto/negative/mixed cases
// that PaddingExtractor doesn't need to worry about.

import com.styleconverter.test.style.core.types.LengthUnit
import com.styleconverter.test.style.core.types.LengthValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    @Test fun `absolute px on all four sides`() {
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginTop", """{"px":20.0}"""),
            pair("MarginRight", """{"px":30.0}"""),
            pair("MarginBottom", """{"px":40.0}"""),
            pair("MarginLeft", """{"px":50.0}"""),
        ))
        assertEquals(MarginValue.Length(LengthValue.Exact(20.0)), cfg.top)
        assertEquals(MarginValue.Length(LengthValue.Exact(50.0)), cfg.left)
        assertTrue(cfg.hasMargin)
    }

    @Test fun `negative px preserved as Length with negative Exact`() {
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginTop", """{"px":-10.0}"""),
        ))
        assertEquals(MarginValue.Length(LengthValue.Exact(-10.0)), cfg.top)
    }

    @Test fun `auto keyword maps to MarginValue-Auto`() {
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginLeft", """"auto""""),
        ))
        assertEquals(MarginValue.Auto, cfg.left)
        assertTrue(cfg.hasHorizontalAuto)
    }

    @Test fun `margin auto 0 mixed auto and zero length`() {
        // CSS `margin: 0 auto` → top/bottom {px:0}, left/right "auto".
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginTop", """{"px":0.0}"""),
            pair("MarginRight", """"auto""""),
            pair("MarginBottom", """{"px":0.0}"""),
            pair("MarginLeft", """"auto""""),
        ))
        assertEquals(MarginValue.Length(LengthValue.Exact(0.0)), cfg.top)
        assertEquals(MarginValue.Auto, cfg.left)
        assertEquals(MarginValue.Auto, cfg.right)
        assertTrue(cfg.hasHorizontalAuto)
        assertEquals(false, cfg.hasVerticalAuto)
    }

    @Test fun `em relative unit propagates through MarginValue-Length`() {
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginTop", """{"original":{"v":1.5,"u":"EM"}}"""),
        ))
        assertEquals(
            MarginValue.Length(LengthValue.Relative(1.5, LengthUnit.EM, null)),
            cfg.top,
        )
    }

    @Test fun `bare number margin treated as percent`() {
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginTop", "10.0"),
        ))
        assertEquals(
            MarginValue.Length(LengthValue.Relative(10.0, LengthUnit.PERCENT, null)),
            cfg.top,
        )
    }

    @Test fun `logical MarginBlockStart stored separately from Top`() {
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginBlockStart", """{"px":4.0}"""),
        ))
        assertEquals(MarginValue.Length(LengthValue.Exact(4.0)), cfg.blockStart)
        assertNull(cfg.top)
    }

    @Test fun `isMarginProperty matches all 8 longhands`() {
        val all = listOf(
            "MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
            "MarginBlockStart", "MarginBlockEnd",
            "MarginInlineStart", "MarginInlineEnd",
        )
        for (name in all) assertTrue(name, MarginExtractor.isMarginProperty(name))
        assertEquals(false, MarginExtractor.isMarginProperty("PaddingTop"))
    }
}
