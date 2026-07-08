package com.styleconverter.test.style.spacing

// Unit tests for PaddingExtractor. Each test mirrors an actual IR shape we
// saw in examples/properties/spacing/padding-*.json (fixtures verified via
// `./gradlew run` on main and piped through python).

import com.styleconverter.test.style.core.types.LengthUnit
import com.styleconverter.test.style.core.types.LengthValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaddingExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    @Test fun `absolute px on all four physical sides`() {
        val cfg = PaddingExtractor.extract(listOf(
            pair("PaddingTop", """{"px":20.0}"""),
            pair("PaddingRight", """{"px":30.0}"""),
            pair("PaddingBottom", """{"px":40.0}"""),
            pair("PaddingLeft", """{"px":50.0}"""),
        ))
        assertEquals(LengthValue.Exact(20.0), cfg.top)
        assertEquals(LengthValue.Exact(30.0), cfg.right)
        assertEquals(LengthValue.Exact(40.0), cfg.bottom)
        assertEquals(LengthValue.Exact(50.0), cfg.left)
        assertTrue(cfg.hasPadding)
    }

    @Test fun `absolute pt normalizes to px via extractLength`() {
        // 10pt → px=13.333... with original kept.
        val cfg = PaddingExtractor.extract(listOf(
            pair("PaddingTop", """{"px":13.333333333333332,"original":{"v":10.0,"u":"PT"}}""")
        ))
        assertTrue(cfg.top is LengthValue.Exact)
        assertEquals(13.333333333333332, (cfg.top as LengthValue.Exact).px, 1e-6)
    }

    @Test fun `em relative length preserved with unit tag`() {
        val cfg = PaddingExtractor.extract(listOf(
            pair("PaddingTop", """{"original":{"v":2.0,"u":"EM"}}"""),
        ))
        assertEquals(LengthValue.Relative(2.0, LengthUnit.EM, null), cfg.top)
    }

    @Test fun `bare number padding treated as percent`() {
        // Padding_Percent_10 emits bare 10.0 for each side.
        val cfg = PaddingExtractor.extract(listOf(
            pair("PaddingTop", "10.0"),
            pair("PaddingRight", "10.0"),
            pair("PaddingBottom", "10.0"),
            pair("PaddingLeft", "10.0"),
        ))
        assertEquals(LengthValue.Relative(10.0, LengthUnit.PERCENT, null), cfg.top)
        assertEquals(LengthValue.Relative(10.0, LengthUnit.PERCENT, null), cfg.left)
    }

    @Test fun `calc expression preserved as Calc variant`() {
        val cfg = PaddingExtractor.extract(listOf(
            pair("PaddingTop", """{"expr":"calc(10px + 5px)"}"""),
        ))
        assertEquals(LengthValue.Calc("calc(10px + 5px)"), cfg.top)
    }

    @Test fun `logical sides stored in logical slots`() {
        val cfg = PaddingExtractor.extract(listOf(
            pair("PaddingBlockStart", """{"px":4.0}"""),
            pair("PaddingInlineEnd", """{"px":8.0}"""),
        ))
        assertEquals(LengthValue.Exact(4.0), cfg.blockStart)
        assertEquals(LengthValue.Exact(8.0), cfg.inlineEnd)
        assertNull(cfg.top)
        assertNull(cfg.right)
    }

    @Test fun `resolve LTR maps inlineStart-to-left and inlineEnd-to-right`() {
        val cfg = PaddingExtractor.extract(listOf(
            pair("PaddingInlineStart", """{"px":3.0}"""),
            pair("PaddingInlineEnd", """{"px":7.0}"""),
        ))
        val r = cfg.resolve(isRtl = false)
        assertEquals(LengthValue.Exact(3.0), r.left)
        assertEquals(LengthValue.Exact(7.0), r.right)
    }

    @Test fun `resolve physical side wins over logical side`() {
        val cfg = PaddingExtractor.extract(listOf(
            pair("PaddingTop", """{"px":1.0}"""),
            pair("PaddingBlockStart", """{"px":99.0}"""),
        ))
        val r = cfg.resolve(isRtl = false)
        assertEquals(LengthValue.Exact(1.0), r.top)
    }

    @Test fun `hasPadding false on empty list`() {
        val cfg = PaddingExtractor.extract(emptyList())
        assertEquals(false, cfg.hasPadding)
    }

    @Test fun `isPaddingProperty matches all 8 longhands`() {
        val all = listOf(
            "PaddingTop", "PaddingRight", "PaddingBottom", "PaddingLeft",
            "PaddingBlockStart", "PaddingBlockEnd",
            "PaddingInlineStart", "PaddingInlineEnd",
        )
        for (name in all) assertTrue(name, PaddingExtractor.isPaddingProperty(name))
        assertEquals(false, PaddingExtractor.isPaddingProperty("MarginTop"))
    }
}
