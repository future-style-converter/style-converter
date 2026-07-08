package com.styleconverter.test.style.sizing

// Unit tests for Phase 3 SizingExtractor. Each fixture shape was copy-pasted
// out of examples/properties/sizing/*.json so parser drift would fail these
// tests before it reached the Applier.

import com.styleconverter.test.style.core.types.LengthUnit
import com.styleconverter.test.style.core.types.LengthValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SizingExtractorTest {

    // Helpers — same style as PaddingExtractorTest so future edits are uniform.
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    @Test fun `width absolute px parses to Exact`() {
        // width-absolute.json → Width_200px.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("Width", """{"type":"length","px":200.0}"""),
        ))
        assertEquals(LengthValue.Exact(200.0), cfg.width)
    }

    @Test fun `width em lands as Relative EM with no px fallback`() {
        // width-units.json → Width_Rem_10.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("Width", """{"type":"length","original":{"v":10.0,"u":"REM"}}"""),
        ))
        assertEquals(LengthValue.Relative(10.0, LengthUnit.REM, null), cfg.width)
    }

    @Test fun `width percentage wrapper parses to Relative PERCENT`() {
        // Width_Percent_50 — uses the WidthValue {type:percentage} shape.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("Width", """{"type":"percentage","value":50.0}"""),
        ))
        assertEquals(LengthValue.Relative(50.0, LengthUnit.PERCENT, null), cfg.width)
    }

    @Test fun `width auto parses to LengthValue Auto`() {
        // width-intrinsic.json → Width_Auto.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("Width", """"auto""""),
        ))
        assertEquals(LengthValue.Auto, cfg.width)
    }

    @Test fun `width min-content and max-content parse to Intrinsic`() {
        val cfgMin = SizingExtractor.extractSizingConfig(listOf(
            pair("Width", """"min-content""""),
        ))
        assertEquals(
            LengthValue.Intrinsic(LengthValue.IntrinsicKind.MIN_CONTENT),
            cfgMin.width,
        )
        val cfgMax = SizingExtractor.extractSizingConfig(listOf(
            pair("Width", """"max-content""""),
        ))
        assertEquals(
            LengthValue.Intrinsic(LengthValue.IntrinsicKind.MAX_CONTENT),
            cfgMax.width,
        )
    }

    @Test fun `bounded fit-content carries bound on width`() {
        // Width_FitContent_Bounded_200px.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("Width", """{"fit-content":{"px":200.0}}"""),
        ))
        assertTrue(cfg.width is LengthValue.Intrinsic)
        val iv = cfg.width as LengthValue.Intrinsic
        assertEquals(LengthValue.IntrinsicKind.FIT_CONTENT, iv.kind)
        assertEquals(LengthValue.Exact(200.0), iv.bound)
    }

    @Test fun `maxwidth none yields LengthValue None distinct from null`() {
        // width-constraints.json → MaxWidth_None.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("MaxWidth", """{"type":"none"}"""),
        ))
        // null would mean "not specified"; None means "explicit none keyword".
        assertEquals(LengthValue.None, cfg.maxWidth)
    }

    @Test fun `logical block-size uses raw px shape`() {
        // logical-sizing.json → BlockSize_100px emits {"px":100.0} directly.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BlockSize", """{"px":100.0}"""),
        ))
        assertEquals(LengthValue.Exact(100.0), cfg.blockSize)
    }

    @Test fun `logical inline-size bare number treated as percent`() {
        // Defensive: the IR emits bare numbers for percent on SizeValue shape
        // just like padding does. Verify the extractor routes to PERCENT.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("InlineSize", "25.0"),
        ))
        assertEquals(LengthValue.Relative(25.0, LengthUnit.PERCENT, null), cfg.inlineSize)
    }

    @Test fun `aspect-ratio routes to extractAspectRatio`() {
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("AspectRatio", """{"ratio":{"w":16.0,"h":9.0},"normalizedRatio":1.7777777777777777}"""),
        ))
        assertNotNull(cfg.aspectRatio)
        assertEquals(1.7777777777777777, cfg.aspectRatio!!.ratio, 1e-12)
    }

    @Test fun `min and max constraints coexist`() {
        // MinMax_Width_100_300 — both bounds set simultaneously.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("MinWidth", """{"type":"length","px":100.0}"""),
            pair("MaxWidth", """{"type":"length","px":300.0}"""),
        ))
        assertEquals(LengthValue.Exact(100.0), cfg.minWidth)
        assertEquals(LengthValue.Exact(300.0), cfg.maxWidth)
        assertTrue(cfg.hasSizing)
        assertTrue(cfg.hasWidthConstraints)
    }

    @Test fun `non-sizing properties ignored`() {
        // Extractor must skip unrelated IR entries without touching the config.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BackgroundColor", """{"srgb":{"r":1.0,"g":0.0,"b":0.0,"a":1.0}}"""),
        ))
        assertNull(cfg.width)
        assertNull(cfg.aspectRatio)
        assertTrue(!cfg.hasSizing)
    }
}
