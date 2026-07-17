package com.styleconverter.runtime.sizing

// Lane BX — `box-sizing: content-box` on the Android sizing lane.
//
// Pins three behaviours the visual gate depends on:
//   1. TRI-STATE extraction — an ABSENT box-sizing must stay null on
//      SizingConfig (unset ≠ content-box), because the whole width+padding
//      fixture corpus is captured against web's `* { box-sizing: border-box }`
//      reset and any implicit CONTENT_BOX default would inflate every frame.
//      (The performance/ lane's BoxModelConfig still defaults to CONTENT_BOX
//      for wire-compat — this suite proves that default never reaches sizing.)
//   2. Inflation arithmetic — css-sizing-3 §3: frame = content + padding +
//      border, e.g. width 100 + padding 16×2 + border 2×2 → frame 136.
//   3. Border-box sentinel — an EXPLICIT border-box (the fixture's passing
//      V1_border variant) and the unset case both leave values untouched.
//
// Wire shapes copy-pasted from the existing extractor suites so parser drift
// fails here first (SizingExtractorTest / BorderExtractorsTest style).

import com.styleconverter.runtime.core.types.LengthValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoxSizingContentBoxTest {

    // Helpers — same style as SizingExtractorTest so future edits are uniform.
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    // ── 1. Tri-state extraction ─────────────────────────────────────────────

    @Test fun `absent box-sizing stays null - unset is not content-box`() {
        // The regression sentinel: width+padding WITHOUT box-sizing must not
        // pick up any content-box default from anywhere (esp. not the
        // performance lane's CONTENT_BOX-defaulting BoxModelConfig).
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("Width", """{"type":"length","px":100.0}"""),
            pair("PaddingLeft", """{"px":16.0}"""),
            pair("PaddingRight", """{"px":16.0}"""),
        ))
        assertNull(cfg.boxSizing)
        // No declaration → no inflation bands either.
        assertEquals(0f, cfg.contentBoxInflateX, 0f)
        assertEquals(0f, cfg.contentBoxInflateY, 0f)
    }

    @Test fun `explicit content-box and border-box decode from the SHOUTY wire`() {
        // Wire shape per BoxSizingPropertyParser.kt (flattened single-field
        // data class): bare "CONTENT_BOX" / "BORDER_BOX" strings.
        val content = SizingExtractor.extractSizingConfig(listOf(
            pair("BoxSizing", """"CONTENT_BOX""""),
        ))
        assertEquals(BoxSizingKeyword.CONTENT_BOX, content.boxSizing)
        val border = SizingExtractor.extractSizingConfig(listOf(
            pair("BoxSizing", """"BORDER_BOX""""),
        ))
        assertEquals(BoxSizingKeyword.BORDER_BOX, border.boxSizing)
    }

    @Test fun `unknown box-sizing token leaves the slot unset`() {
        // Wire drift must never guess content-box (that would inflate frames
        // against every border-box baseline) — unknown tokens stay null.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BoxSizing", """"PADDING_BOX""""),
        ))
        assertNull(cfg.boxSizing)
    }

    // ── 2. Inflation bands (padding + used border widths) ───────────────────

    @Test fun `content-box computes padding plus border inflation per axis`() {
        // The task's canonical arithmetic: padding 16 all round + a painting
        // 2px border all round → 36px of inflation on each axis.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BoxSizing", """"CONTENT_BOX""""),
            pair("Width", """{"type":"length","px":100.0}"""),
            pair("PaddingTop", """{"px":16.0}"""),
            pair("PaddingRight", """{"px":16.0}"""),
            pair("PaddingBottom", """{"px":16.0}"""),
            pair("PaddingLeft", """{"px":16.0}"""),
            // Border shorthand longhands — width AND style, because a side
            // without a visible style has USED width 0 (CSS 2.1 §8.5.3).
            pair("BorderWidth", """{"px":2.0}"""),
            pair("BorderStyle", """{"keyword":"SOLID"}"""),
        ))
        assertEquals(BoxSizingKeyword.CONTENT_BOX, cfg.boxSizing)
        assertEquals(36f, cfg.contentBoxInflateX, 1e-4f)
        assertEquals(36f, cfg.contentBoxInflateY, 1e-4f)
    }

    @Test fun `border without style contributes zero band - used width is 0`() {
        // CSS 2.1 §8.5.3: border-style's initial is `none`, so a bare
        // border-width never paints nor consumes layout space — the band
        // gate must mirror StyleApplier.borderBandInsets exactly.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BoxSizing", """"CONTENT_BOX""""),
            pair("PaddingLeft", """{"px":10.0}"""),
            pair("PaddingRight", """{"px":10.0}"""),
            pair("BorderWidth", """{"px":4.0}"""),  // style-less → used width 0
        ))
        assertEquals(20f, cfg.contentBoxInflateX, 1e-4f)
        assertEquals(0f, cfg.contentBoxInflateY, 1e-4f)
    }

    @Test fun `explicit border-box computes no inflation bands`() {
        // V1_border sentinel: explicit border-box keeps 0f bands so the
        // Applier path is provably identical to the unset case.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BoxSizing", """"BORDER_BOX""""),
            pair("Width", """{"type":"length","px":180.0}"""),
            pair("PaddingTop", """{"px":10.0}"""),
        ))
        assertEquals(BoxSizingKeyword.BORDER_BOX, cfg.boxSizing)
        assertEquals(0f, cfg.contentBoxInflateX, 0f)
        assertEquals(0f, cfg.contentBoxInflateY, 0f)
    }

    // ── 3. Applier arithmetic (pure, no Compose runtime needed) ─────────────

    @Test fun `content-box inflates exact width by padding plus border`() {
        // width 100 + padding 16×2 + border 2×2 → frame 136 (css-sizing-3 §3).
        val out = SizingApplier.inflateForContentBox(
            LengthValue.Exact(100.0), BoxSizingKeyword.CONTENT_BOX, 36f)
        assertEquals(LengthValue.Exact(136.0), out)
    }

    @Test fun `unset and border-box leave the exact value untouched`() {
        // Tri-state guard: null (unset) and explicit BORDER_BOX are both the
        // border-box status quo — byte-identical values out.
        assertEquals(
            LengthValue.Exact(180.0),
            SizingApplier.inflateForContentBox(LengthValue.Exact(180.0), null, 36f))
        assertEquals(
            LengthValue.Exact(180.0),
            SizingApplier.inflateForContentBox(
                LengthValue.Exact(180.0), BoxSizingKeyword.BORDER_BOX, 36f))
    }

    @Test fun `non-exact shapes pass through even under content-box`() {
        // auto / intrinsic / percent have no definite px to reinterpret —
        // they must flow to the Applier unchanged (documented divergence,
        // not a silent one — see inflateForContentBox's doc comment).
        assertEquals(
            LengthValue.Auto,
            SizingApplier.inflateForContentBox(
                LengthValue.Auto, BoxSizingKeyword.CONTENT_BOX, 36f))
        assertNull(SizingApplier.inflateForContentBox(
            null, BoxSizingKeyword.CONTENT_BOX, 36f))
    }
}
