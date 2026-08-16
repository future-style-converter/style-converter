package com.styleconverter.runtime.sizing

// Wave 44 (lane U6) — content-box inflation for RELATIVE sizes.
//
// css-sizing-3 §3: under `box-sizing: content-box` the declared width/height
// size the CONTENT box, so the frame = declared + padding + border. The
// wave-11 inflateForContentBox only handled LengthValue.Exact — a Relative
// shape (lh/em/ch) passed through and rendered its resolved px as the
// BORDER box, dropping the band.
//
// The measured defect (wave43-final css-overflow captures, pixels not vibes):
// css-overflow/line-clamp/discard/discard-multicol-001 (002/004 share the
// stylesheet) declares `font-family: monospace; height: 2lh; border: 1px
// solid` and no box-sizing → the WPT capture default is CONTENT_BOX. With
// the wave-43 lh basis (13px monospace-UA font × the 1.25 calibrated
// composed ratio) the height resolves 32.5px — but Android emitted 32.5 as
// the frame (capture rows 104..136 = 33px) where the browser-ref's frame is
// 32.5 content + 2×1px borders = 34.5 (ref rows 88..122 = 35px): the second
// text row of every column clipped mid-glyph under the bottom border.
// (iOS needs no twin fix: its SizeApplier.inflatedAxis already runs on the
// RESOLVED effW/effH CGFloats whatever the source unit — the wave43-final
// iOS capture's frame measures the ref's own 35px.)
//
// Blast radius beyond this repro (wave-44 skeptic S5, D2): 40 wave43-final
// tests carry a component with a non-% Relative size AND a nonzero band.
// The band is correct per §3, but which way a given capture moves against
// its ref depends on per-platform font metrics — two measured
// counterexamples (hyphens-auto-control, hyphens-vertical-001) are recorded
// in SizingApplier.inflateForContentBox's doc, so nothing here should be
// read as "the affected cells can only improve".
//
// Wire shapes below are copied VERBATIM from the consumed IR at
// tools/titan/runs/wave43-final/sections/css-overflow/per-test-ir/
// wpt__css-overflow__line-clamp__discard__discard-multicol-001.json.

import com.styleconverter.runtime.core.renderer.REF_DEFAULT_FONT_LINE_HEIGHT_RATIO
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.spacing.LhUnitLineHeight
import com.styleconverter.runtime.spacing.SpacingContext
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ContentBoxRelativeInflationTest {

    // Helpers — same style as BoxSizingContentBoxTest so edits stay uniform.
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    // The discard-multicol-001 `.test` div, verbatim per-test-ir wire: the
    // sizing-relevant slice (gaps/columns/margins ride along untouched to
    // prove the extractor walk tolerates them; Continue is line-clamp's).
    private val discardMulticol001 = listOf(
        pair("FontFamily", """["monospace"]"""),
        pair("RowGap", """{"type":"length","original":{"v":1,"u":"CH"}}"""),
        pair("ColumnGap", """{"type":"length","original":{"v":1,"u":"CH"}}"""),
        pair("Width", """{"type":"length","original":{"v":27,"u":"CH"}}"""),
        pair("ColumnCount", """3"""),
        pair("Height", """{"type":"length","original":{"v":2,"u":"LH"}}"""),
        pair("BorderTopWidth", """{"px":1}"""),
        pair("BorderRightWidth", """{"px":1}"""),
        pair("BorderBottomWidth", """{"px":1}"""),
        pair("BorderLeftWidth", """{"px":1}"""),
        pair("BorderTopStyle", """"SOLID""""),
        pair("BorderRightStyle", """"SOLID""""),
        pair("BorderBottomStyle", """"SOLID""""),
        pair("BorderLeftStyle", """"SOLID""""),
        pair("MarginTop", """{"original":{"v":1,"u":"EM"}}"""),
        pair("MarginRight", """{"original":{"v":1,"u":"EM"}}"""),
        pair("MarginBottom", """{"original":{"v":1,"u":"EM"}}"""),
        pair("MarginLeft", """{"original":{"v":1,"u":"EM"}}"""),
        pair("Continue", """"DISCARD""""),
    )

    // The resolution context the live path builds for this component:
    // fontSizePx 13 — MonospaceUAFontSize's fixed default (no font-size +
    // first family `monospace`); lineHeightPx through the REAL wave-43
    // production pick (nothing declared + WPT capture → the calibrated
    // 1.25 × 13 = 16.25 composed-grid pin), so this test breaks if the lh
    // channel and the inflation lane ever disagree about the basis.
    private val ctx001 = SpacingContext(
        fontSizePx = 13f,
        lineHeightPx = LhUnitLineHeight.usedLineHeightPx(
            declaredLineHeightPx = null,   // the wire carries no LineHeight
            declaredNormal = false,        // and no `normal` keyword either
            fontSizePx = 13f,              // same basis as the context above
            wptCapture = true,             // the TITAN composed-capture path
        ),
    )

    // ── the verbatim repro: 2lh + 1px borders → the 34.5px frame ──────────

    @Test fun `discard-multicol-001 - 2lh height inflates to the 34 point 5 px ref frame`() {
        // WPT capture folds the undeclared box-sizing to CONTENT_BOX and
        // computes the border bands: 1px × 2 sides per axis, no padding.
        val cfg = SizingExtractor.extractSizingConfig(discardMulticol001, wptCaptureMode = true)
        assertEquals(BoxSizingKeyword.CONTENT_BOX, cfg.boxSizing)
        assertEquals(2f, cfg.contentBoxInflateX, 1e-4f)
        assertEquals(2f, cfg.contentBoxInflateY, 1e-4f)
        // The height survived extraction as the raw Relative lh shape.
        assertEquals(LengthValue.Relative(2.0, LengthUnit.LH, null), cfg.height)
        // The lh channel resolved the calibrated basis: 13 × 1.25 = 16.25.
        assertEquals(13f * REF_DEFAULT_FONT_LINE_HEIGHT_RATIO, ctx001.lineHeightPx!!, 1e-4f)
        // Inflation: content 2 × 16.25 = 32.5 + the 2px band → frame 34.5,
        // the ref's own 88..122 = 35-device-px border box (34.5 rounded).
        assertEquals(
            LengthValue.Exact(34.5),
            SizingApplier.inflateForContentBox(
                cfg.height, cfg.boxSizing, cfg.contentBoxInflateY, ctx001))
    }

    @Test fun `discard-multicol-001 - 27ch width gains the same 2px band`() {
        // Width twin: 27ch resolves against the measured '0' advance (the
        // android platform text engine reports 8.0px at monospace-13 —
        // capture cols 32..247 = 216px pre-fix) and gains the border band:
        // frame = 27 × 8 + 2 = 218. (The ref's 213 = 27 × Chromium's 7.8
        // advance + 2 — the residual is FONT METRICS, not box arithmetic.)
        // Read that direction honestly (wave-44 skeptic S5, D2): this axis
        // moves 216 → 218 while the ref sits at 213, i.e. FARTHER from the
        // ref, because the band lands on top of a basis Android already
        // overshoots. The band is what §3 requires; the movement of a given
        // capture is not "toward the ref by construction" — see the
        // measured simulation table in SizingApplier.inflateForContentBox
        // (hyphens-auto-control and hyphens-vertical-001 both lose ~0.002
        // mssim, hyphens-manual-010 gains 0.0002).
        val cfg = SizingExtractor.extractSizingConfig(discardMulticol001, wptCaptureMode = true)
        assertEquals(LengthValue.Relative(27.0, LengthUnit.CH, null), cfg.width)
        assertEquals(
            LengthValue.Exact(218.0),
            SizingApplier.inflateForContentBox(
                cfg.width, cfg.boxSizing, cfg.contentBoxInflateX,
                // Same context + the deterministic stand-in for the live
                // ChUnitMetrics measurement (the applier threads it).
                ctx001.copy(chAdvancePx = 8f)))
    }

    // ── the em case the lane brief asks for ───────────────────────────────

    @Test fun `em height under content-box resolves then gains the band`() {
        // `height: 2em` at a 13px font = 26px content; with a 2px border
        // band the frame is 28 (css-sizing-3 §3 — same rule, em basis).
        assertEquals(
            LengthValue.Exact(28.0),
            SizingApplier.inflateForContentBox(
                LengthValue.Relative(2.0, LengthUnit.EM, null),
                BoxSizingKeyword.CONTENT_BOX, 2f,
                SpacingContext(fontSizePx = 13f)))
    }

    // ── byte-stability gates: what must NOT move ──────────────────────────

    @Test fun `zero band keeps a relative size shape-identical`() {
        // No padding/border → nothing to add; converting Relative→Exact
        // would only flip the apply branch (width() → exactWidth) with no
        // geometric change, so the SAME instance must flow through — this
        // is what pins every borderless WPT test byte-identical.
        val v = LengthValue.Relative(2.0, LengthUnit.LH, null)
        assertSame(v, SizingApplier.inflateForContentBox(
            v, BoxSizingKeyword.CONTENT_BOX, 0f, ctx001))
    }

    @Test fun `unset and explicit border-box keep relative sizes untouched`() {
        // The tri-state guard is shape-independent: outside an explicit
        // CONTENT_BOX the value passes through whatever its unit — the
        // dark-stage corpus (grep wave 44: the only committed content-box
        // fixture is all-px) can never reach the new arm.
        val v = LengthValue.Relative(2.0, LengthUnit.LH, null)
        assertSame(v, SizingApplier.inflateForContentBox(v, null, 2f, ctx001))
        assertSame(v, SizingApplier.inflateForContentBox(
            v, BoxSizingKeyword.BORDER_BOX, 2f, ctx001))
    }

    @Test fun `percent stays fractional - documented pass-through`() {
        // % routes to fillMaxWidth/Height as a FRACTION of the parent; a
        // px band cannot be folded into a fraction statically, so the
        // shape passes through (the honest divergence stated in
        // inflateForContentBox's doc — a layout-time add is future work).
        val v = LengthValue.Relative(50.0, LengthUnit.PERCENT, null)
        assertSame(v, SizingApplier.inflateForContentBox(
            v, BoxSizingKeyword.CONTENT_BOX, 2f, ctx001))
    }

    @Test fun `non-WPT extraction of the same IR never reaches the new arm`() {
        // Outside WPT capture the undeclared box-sizing stays null (the
        // tri-state pin), so the discard-multicol IR itself is inert on
        // the dark stage: no CONTENT_BOX, no bands, value untouched.
        val cfg = SizingExtractor.extractSizingConfig(discardMulticol001)
        assertNull(cfg.boxSizing)
        assertEquals(0f, cfg.contentBoxInflateY, 0f)
        assertSame(cfg.height, SizingApplier.inflateForContentBox(
            cfg.height, cfg.boxSizing, cfg.contentBoxInflateY, ctx001))
    }
}
