package com.styleconverter.runtime.sizing

// Wave-18 lane 2 — pin P13: percent padding in the content-box frame
// inflation resolves against ZERO when no definite containing-block basis
// exists (css-sizing-3 §5.2.1: cyclic percentages in intrinsic sizing
// resolve against zero). The extractor runs statically, where no basis is
// ever known, so a percent band must contribute NOTHING to the frame.
//
// The motivating failure (css-sizing abspos-auto-sizing-fit-content-
// percentage-003/004): `width: 100px; padding-left: 50%` under the WPT
// content-box default inflated the frame by 50% × 390 (viewport fallback)
// → 295px painted vs the ref's 100px. Wire shapes below are copied from
// the live IR at tools/titan/runs/wave18-gate/sections/css-sizing/
// per-test-ir/wpt__css-sizing__abspos-auto-sizing-fit-content-percentage-003.json.

import com.styleconverter.runtime.core.types.LengthValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentBoxPercentInflationTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    // The padded child of abspos-003, verbatim: Width/Height typed px,
    // PaddingLeft as the converter's bare-number percent encoding.
    private val abspos003Child = listOf(
        pair("Width", """{"type":"length","px":100}"""),
        pair("Height", """{"type":"length","px":100}"""),
        pair("PaddingLeft", """50"""),
    )

    @Test fun `percent padding contributes zero inflation under the WPT content-box default`() {
        // WPT capture folds the undeclared box-sizing to CONTENT_BOX; the
        // 50% band has no static basis → 0 inflation (P13), keeping the
        // painted frame at the declared 100px like the browser ref.
        val cfg = SizingExtractor.extractSizingConfig(abspos003Child, wptCaptureMode = true)
        assertEquals(BoxSizingKeyword.CONTENT_BOX, cfg.boxSizing)
        assertEquals(0f, cfg.contentBoxInflateX, 0f)
        assertEquals(0f, cfg.contentBoxInflateY, 0f)
        // End-to-end: the applier's inflate keeps the 100px frame.
        assertEquals(
            LengthValue.Exact(100.0),
            SizingApplier.inflateForContentBox(
                LengthValue.Exact(100.0), cfg.boxSizing, cfg.contentBoxInflateX))
    }

    @Test fun `absolute padding still inflates the content-box frame`() {
        // Sanity guard: the §5.2.1 zero rule is PERCENT-only — a px band
        // must keep the wave-11 inflation arithmetic (100 + 20 = 120).
        val cfg = SizingExtractor.extractSizingConfig(
            listOf(
                pair("Width", """{"type":"length","px":100}"""),
                pair("PaddingLeft", """{"px":20.0}"""),
            ),
            wptCaptureMode = true)
        assertEquals(20f, cfg.contentBoxInflateX, 1e-4f)
        assertEquals(
            LengthValue.Exact(120.0),
            SizingApplier.inflateForContentBox(
                LengthValue.Exact(100.0), cfg.boxSizing, cfg.contentBoxInflateX))
    }

    @Test fun `dark stage is untouched — no box-sizing means no inflation at all`() {
        // Outside WPT capture the tri-state stays null and the band gate
        // never runs: byte-identical to the frozen baseline corpus.
        val cfg = SizingExtractor.extractSizingConfig(abspos003Child)
        assertEquals(null, cfg.boxSizing)
        assertEquals(0f, cfg.contentBoxInflateX, 0f)
    }
}
