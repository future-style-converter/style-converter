package com.styleconverter.test.style.effects.filter

// Phase 8 functional tests for FilterExtractor. Exercises the
// filter-function list parsing: blur/brightness/contrast/grayscale/invert/
// hue-rotate/saturate/sepia/opacity/drop-shadow. The IR form is always an
// array of {"fn": "<name>", ...} objects (see CLAUDE.md "IR Format" block).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test
    fun `Filter blur function parses to Blur with Dp radius`() {
        // blur(5px) — single-function chain.
        val cfg = FilterExtractor.extractFilterConfig(
            listOf(pair("Filter", "[{\"fn\":\"blur\",\"r\":{\"px\":5.0}}]"))
        )
        assertTrue("has filters", cfg.hasFilters)
        val blur = cfg.filters.first()
        assertTrue(blur is FilterFunction.Blur)
        assertEquals(5f, (blur as FilterFunction.Blur).radius.value, 0.01f)
    }

    @Test
    fun `Filter brightness amount parses`() {
        // brightness(120%) — IR carries the raw percentage (120); the
        // extractor divides by 100 so the applier's ColorMatrix sees a
        // multiplier (1.2 = 120% brightness).
        val cfg = FilterExtractor.extractFilterConfig(
            listOf(pair("Filter", "[{\"fn\":\"brightness\",\"v\":120.0}]"))
        )
        val br = cfg.filters.first() as FilterFunction.Brightness
        assertEquals(1.2f, br.amount, 0.01f)
    }

    @Test
    fun `Filter multi-function chain preserves order`() {
        // Multiple filters compose in LEFT-TO-RIGHT order per CSS spec —
        // the extractor must preserve array order so the applier can fold
        // the ColorMatrix in the right direction (matters for brightness*sepia
        // producing a DIFFERENT result from sepia*brightness).
        val cfg = FilterExtractor.extractFilterConfig(
            listOf(pair("Filter", "[" +
                "{\"fn\":\"brightness\",\"v\":1.2}," +
                "{\"fn\":\"sepia\",\"v\":0.5}," +
                "{\"fn\":\"blur\",\"r\":{\"px\":2.0}}" +
            "]"))
        )
        assertEquals(3, cfg.filters.size)
        assertTrue(cfg.filters[0] is FilterFunction.Brightness)
        assertTrue(cfg.filters[1] is FilterFunction.Sepia)
        assertTrue(cfg.filters[2] is FilterFunction.Blur)
    }

    @Test
    fun `BackdropFilter goes into separate bucket`() {
        // BackdropFilter and Filter share the same extraction but live in
        // distinct fields so the applier can direct element-filters to
        // graphicsLayer while backdrop-filters hit RenderEffect for the
        // area behind the element.
        val cfg = FilterExtractor.extractFilterConfig(
            listOf(pair("BackdropFilter", "[{\"fn\":\"blur\",\"r\":{\"px\":10.0}}]"))
        )
        assertTrue(cfg.hasBackdropFilters)
        assertTrue("no element filters", !cfg.hasFilters)
    }

    @Test
    fun `grayscale invert saturate hue-rotate parse`() {
        // One test covering the remaining ColorMatrix filters — these all
        // share the same {v: amount} or {angle: degrees} IR shape.
        val cfg = FilterExtractor.extractFilterConfig(
            listOf(pair("Filter", "[" +
                "{\"fn\":\"grayscale\",\"v\":1.0}," +
                "{\"fn\":\"invert\",\"v\":0.5}," +
                "{\"fn\":\"saturate\",\"v\":2.0}," +
                "{\"fn\":\"hue-rotate\",\"angle\":{\"degrees\":90.0}}" +
            "]"))
        )
        assertEquals(4, cfg.filters.size)
    }

    @Test
    fun `empty Filter array produces hasFilters=false`() {
        val cfg = FilterExtractor.extractFilterConfig(
            listOf(pair("Filter", "[]"))
        )
        assertTrue("empty array is not a filter", !cfg.hasFilters)
    }

    @Test
    fun `absent Filter property yields empty config`() {
        val cfg = FilterExtractor.extractFilterConfig(emptyList())
        assertTrue(!cfg.hasAnyFilters)
    }
}
