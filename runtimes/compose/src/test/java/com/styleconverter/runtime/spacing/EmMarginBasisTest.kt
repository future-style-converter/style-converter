package com.styleconverter.runtime.spacing

// Wave-45 lane X4 — the em-margin BASIS: margins declared in `em` must
// resolve against the element's OWN computed font-size (css-values-4
// §5.1.1), including the CSS Fonts 4 §3.5 fixed-default quirk (an element
// with NO font-size declaration whose FIRST declared family is the
// `monospace` generic computes to 13px, not 16 — MonospaceUAFontSize,
// wave 36).
//
// ## The measured defect this pins the fix for
// wave-44 css-overflow/line-clamp/discard/discard-multicol-001 (android-ref
// SSIM 0.8518, wptPass false): both divs declare `font-family: monospace;
// margin: 1em` and no font-size. DynamicValueResolver's pre-pass resolves
// the em against ITS font-size oracle — which lacks the §3.5 quirk — and
// prebakes 16px; the applier then reads the top-level px as canonical. The
// browser ref resolves 13px. Measured on the captures: border left edge
// x=32 (16 canvas pad + 16) vs the ref's x=29 (16 + 13).
//
// ## The fix under test (all in the spacing tree)
//  1. MarginExtractor keeps the prebaked-em shape as Relative(EM,
//     pxFallback=prebake) instead of collapsing to Exact(prebake).
//  2. LayoutFacade threads StyleApplier.buildSpacingContext into
//     SpacingApplier.applyMargin, so MarginApplier's resolveToDp
//     multiplies against the quirk-corrected fontSizePx (13) instead of
//     the 16px default context.
//  3. BlockMarginCollapse classifies the new shape through its fallback,
//     keeping every §8.3.1 plan byte-identical (pinned in
//     BlockMarginCollapseTest).
//
// Wire shapes are VERBATIM from the live wave-44 IR
// (tools/titan/runs/wave44-final/sections/css-overflow/per-test-ir/
// wpt__css-overflow__line-clamp__discard__discard-multicol-001.json):
//   {"type":"FontFamily","data":["monospace"]}
//   {"type":"MarginTop","data":{"original":{"v":1,"u":"EM"}}}   (raw wire)
// plus the post-DynamicValueResolver shape the applier actually receives
// (the pre-pass adds the resolved px NEXT TO the preserved original):
//   {"px":16.0,"original":{"v":1,"u":"EM"}}
// Never invent wire shapes — this file follows LhUnitLineHeightTest's rule.

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.StyleApplier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class EmMarginBasisTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    /** The discard-multicol-001 div's post-resolver property list, reduced
     *  to the pairs this lane consumes (FontFamily + the four em margins —
     *  the ch/lh sizing values are exercised by LhUnitLineHeightTest and
     *  would drag platform text metrics into a pure JVM test). */
    private fun divPairs() = listOf(
        "FontFamily" to parse("""["monospace"]"""),
        "MarginTop" to parse("""{"px":16.0,"original":{"v":1,"u":"EM"}}"""),
        "MarginRight" to parse("""{"px":16.0,"original":{"v":1,"u":"EM"}}"""),
        "MarginBottom" to parse("""{"px":16.0,"original":{"v":1,"u":"EM"}}"""),
        "MarginLeft" to parse("""{"px":16.0,"original":{"v":1,"u":"EM"}}"""),
    )

    @Test fun `spacing context carries the monospace-13 quirk for the div`() {
        // The ctx source the fix resolves against: TypographyExtractor folds
        // MonospaceUAFontSize (no FontSize + monospace-first family ⇒ 13px,
        // CSS Fonts 4 §3.5) into the config, and buildSpacingContext reads
        // it — this is the "which font-size feeds the ctx" verification.
        val ctx = StyleApplier.buildSpacingContext(StyleApplier.extractConfig(divPairs()))
        assertEquals(13f, ctx.fontSizePx, 0f)
    }

    @Test fun `div margins resolve 13px on the quirk basis - the ref geometry`() {
        // End-to-end through the production readers: extractConfig's margin
        // (MarginExtractor's preserved Relative(EM, 16)) resolved with the
        // same context the layout step now threads (LayoutFacade →
        // SpacingApplier.applyMargin). resolvedInsets shares lengthOrZero
        // with the modifier path, so this IS the applied band.
        val config = StyleApplier.extractConfig(divPairs())
        val ctx = StyleApplier.buildSpacingContext(config)
        val insets = MarginApplier.resolvedInsets(config.layout.margin, ctx)
        // 1em × 13px = 13 per side — the ref's margin. Hand-computed target
        // geometry vs the frozen ref (white-black-ink harness, canvas pad
        // 16): border LEFT edge 16 + 13 = 29 (wave-44 capture painted 32).
        // The VERTICAL sides also shrink 16→13; the remaining top-gap
        // residual (capture 104 vs ref 88 for box1's top border) is the
        // composed root-stack fold still ADDING the <p>'s UA bottom margin
        // to the div's own top margin instead of collapsing — that fold
        // lives in the harness's StaticEmMargins (E4 bails without an own
        // FontSize) and is deferred with its exact seam in the lane report.
        assertEquals(MarginInsets(13.dp, 13.dp, 13.dp, 13.dp), insets)
    }

    @Test fun `agreeing-basis prebakes are byte-identical - dotted-001 shape`() {
        // css-text-decor/text-decoration-dotted-001's divs declare
        // `font-size: 92px; margin: .5em` — the pre-pass basis (own declared
        // size) and the ctx agree, so the re-resolution reproduces the
        // prebake exactly: 0.5 × 92 = 46, the same px the old Exact carried.
        val pairs = listOf(
            "FontSize" to parse("""{"px":92.0}"""),
            "MarginTop" to parse("""{"px":46.0,"original":{"v":0.5,"u":"EM"}}"""),
        )
        val config = StyleApplier.extractConfig(pairs)
        val ctx = StyleApplier.buildSpacingContext(config)
        assertEquals(92f, ctx.fontSizePx, 0f)
        val insets = MarginApplier.resolvedInsets(config.layout.margin, ctx)
        assertEquals(46.dp, insets.top)
    }

    @Test fun `default-context callers keep the prebake - legacy stability`() {
        // Every call site that does NOT thread a context (the default
        // SpacingContext has fontSizePx 16) resolves 1em × 16 = 16 — the
        // exact prebaked value, so the dark-stage corpus and the backdrop
        // resolvedInsets read (see MarginApplier's second KNOWN LIMIT) are
        // byte-stable by construction.
        val config = StyleApplier.extractConfig(divPairs())
        val insets = MarginApplier.resolvedInsets(config.layout.margin)
        assertEquals(MarginInsets(16.dp, 16.dp, 16.dp, 16.dp), insets)
    }
}
