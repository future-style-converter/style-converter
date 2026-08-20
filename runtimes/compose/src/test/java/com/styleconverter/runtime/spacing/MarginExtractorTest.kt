package com.styleconverter.runtime.spacing

// Unit tests for MarginExtractor. Emphasis on the auto/negative/mixed cases
// that PaddingExtractor doesn't need to worry about.

import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
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

    // ── wave-45 lane X4: the prebaked-em preservation ────────────────────
    // DynamicValueResolver's pre-pass adds a resolved px NEXT TO an em
    // original (shape `{"px":N,"original":{"v":V,"u":"EM"}}`); the
    // extractor must keep the em identity (with the prebake as fallback)
    // so the applier can re-resolve against the element's OWN computed
    // font-size (css-values-4 §5.1.1 — the CSS Fonts 4 §3.5 monospace-13
    // quirk case measured on wave-44 discard-multicol-001).

    @Test fun `prebaked em keeps Relative with the prebake as pxFallback`() {
        // The exact post-resolver wire of discard-multicol-001's div
        // (margin: 1em prebaked at the 16px default basis).
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginTop", """{"px":16.0,"original":{"v":1.0,"u":"EM"}}"""),
        ))
        assertEquals(
            MarginValue.Length(LengthValue.Relative(1.0, LengthUnit.EM, 16.0)),
            cfg.top,
        )
    }

    @Test fun `prebaked em inside the typed length wrapper is preserved too`() {
        // The `{"type":"length"}` envelope variant the pre-pass preserves.
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginTop", """{"type":"length","px":46.0,"original":{"v":0.5,"u":"EM"}}"""),
        ))
        assertEquals(
            MarginValue.Length(LengthValue.Relative(0.5, LengthUnit.EM, 46.0)),
            cfg.top,
        )
    }

    @Test fun `prebaked NON-em originals keep the canonical Exact px`() {
        // Absolute source units (converter emits px beside e.g. PT): px is
        // canonical, exactly as before wave 45 — only EM re-resolves.
        val cfg = MarginExtractor.extract(listOf(
            pair("MarginTop", """{"px":16.0,"original":{"v":12.0,"u":"PT"}}"""),
            pair("MarginBottom", """{"px":39.0,"original":{"v":10.0,"u":"VW"}}"""),
        ))
        assertEquals(MarginValue.Length(LengthValue.Exact(16.0)), cfg.top)
        assertEquals(MarginValue.Length(LengthValue.Exact(39.0)), cfg.bottom)
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
