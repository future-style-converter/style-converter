package com.styleconverter.runtime.spacing

// The `lh` unit's LINE-HEIGHT SOURCE — wave 43, lane V3.
//
// css-values-4 §6.2.1: `lh` is "equal to the computed value of the
// line-height property of the element on which it is used". Until this
// wave SpacingResolve hardcoded `1.2 × font-size` — the UA default-sheet
// approximation of `normal` — for EVERY element, including ones whose
// capture text demonstrably lays on a different pitch.
//
// ## The defect, measured on the wave42-final gate (pixels, not vibes)
// css-overflow/line-clamp/discard/discard-multicol-001 (and 002/004 — the
// same stylesheet) declares `font-family: monospace; height: 2lh` and no
// line-height. On Android (wave42-final css-overflow captures, SSIM 0.8605):
//   * the border box measures rows 104..134 = 31px → content 29px, i.e.
//     2 × 1.2 × the 13px monospace-UA font size (MonospaceUAFontSize),
//   * while the ref's box is rows 88..122 = 35px → content 33px, i.e.
//     2 × 13 × 1.25 — the corpus-v4.1 REF_LINE_HEIGHT pin the browser-ref
//     injects (`capture-browser-ref.mjs` `:where(…){line-height:1.25}`),
//     the SAME calibrated ratio every composed capture's own text grid
//     uses (WptCaptureMode.REF_DEFAULT_FONT_LINE_HEIGHT_RATIO, consumed by
//     ListMarkerLineBox.resolve / PseudoTextMetrics / PlaceholderContent).
// The second text row is clipped mid-glyph on Android; the ref shows two
// complete rows. The box is short because the lh basis ignored the line
// grid the capture actually lays text on.
//
// ## The three-state contract (the shared LineHeightNormal table, mapped
// onto a NUMERIC unit basis)
// | line-height on the wire   | WPT capture              | elsewhere      |
// |---------------------------|--------------------------|----------------|
// | typed value (px-resolved) | that value, verbatim     | that value     |
// | the `normal` keyword      | calibrated 1.25 × font   | null → 1.2em   |
// | absent                    | calibrated 1.25 × font   | null → 1.2em   |
// A returned null tells SpacingResolve "no source" and its LH arms keep
// the historical 1.2 × font-size fallback — which is what pins the whole
// dark stage byte-identical (no committed fixture under
// fixtures/properties|components uses the lh unit; verified by grep at
// wave 43, so even the declared row cannot move a committed baseline).
//
// ## Two stated approximations, deliberate and documented
// * `normal` under WPT capture maps to the CALIBRATED ratio, not the
//   face's natural metrics (LineHeightNormal's NATURAL row): a static
//   unit resolution needs a number and has no text-engine channel; the
//   pinned composed ratio is the deterministic stand-in the lane brief
//   directs, and no wave42-final lh consumer declares `normal` anyway.
// * The ratio is applied under the WHOLE WPT-capture flag (inbox AND
//   composed) even though the legacy per-component inbox grid is 1.2 —
//   the same simplification LineClampCap shipped in wave 41 (its
//   `composedWpt = true` hardcode); the TITAN gate has been composed-only
//   since corpus v4.
//
// Byte-parallel twin: SwiftUI StyleEngine/spacing/LhUnitLineHeight.swift.
// Web needs no twin — it emits the lh length verbatim and the browser
// resolves it against the real used line-height for free.

import com.styleconverter.runtime.core.renderer.REF_DEFAULT_FONT_LINE_HEIGHT_RATIO
import com.styleconverter.runtime.typography.LineHeightNormal

object LhUnitLineHeight {

    /**
     * The USED line-height (px) the `lh` unit resolves against, or null
     * when no source exists and SpacingResolve must keep its historical
     * 1.2 × font-size fallback (the dark-stage byte-stability contract).
     *
     * @param declaredLineHeightPx the element's DECLARED line-height after
     *   typography extraction, in the runtime's px==sp==dp space —
     *   `config.typography.lineHeight` when Sp-typed (all extractor
     *   outputs are: px lengths verbatim, numbers/percentages folded
     *   × font-size). Null when the wire carried no LineHeight. NOTE:
     *   for the `normal` keyword the extractor DOES emit its legacy 1.2
     *   compatibility number here — [declaredNormal] is what demotes it.
     * @param declaredNormal did the cascade declare the `normal` KEYWORD
     *   (LineHeightNormal.isDeclaredNormal over the same property list)?
     *   Then the number in [declaredLineHeightPx] is the wire's 1.2
     *   stand-in, not an author value, and must not win under capture.
     * @param fontSizePx the element's resolved font size in px — the SAME
     *   value the SpacingContext carries (declared, or the monospace-UA
     *   13px quirk, or the 16px default), so lh and em can never disagree
     *   about the font they measure.
     * @param wptCapture value of LocalWptCaptureMode threaded down the
     *   static chain (StyleApplier.applyProperties) — gates the
     *   calibrated rows exactly as the table above states.
     */
    fun usedLineHeightPx(
        declaredLineHeightPx: Float?,
        declaredNormal: Boolean,
        fontSizePx: Float,
        wptCapture: Boolean,
    ): Float? =
        // Delegate the STATE pick to the shared native decision so this
        // table and the text-run tables (ListMarkerLineBox, the item
        // placeholder, PseudoTextMetrics) can never disagree about which
        // declaration won — one rule, many consumers.
        when (LineHeightNormal.lineBoxSource(
            hasDeclaredValue = declaredLineHeightPx != null,
            declaredNormal = declaredNormal,
            wptCapture = wptCapture,
        )) {
            // An author value wins outright (author > us), verbatim —
            // including an explicit `line-height: 0` (css-inline-3 §4.2
            // allows it; 2lh is then honestly 0). Non-null by
            // construction: DECLARED is only returned when
            // hasDeclaredValue was true above.
            LineHeightNormal.LineBoxSource.DECLARED -> declaredLineHeightPx
            // Declared `normal` under WPT capture — the calibrated
            // composed ratio as the documented numeric stand-in for the
            // face metrics a static resolve cannot look up (see header).
            LineHeightNormal.LineBoxSource.NATURAL ->
                fontSizePx * REF_DEFAULT_FONT_LINE_HEIGHT_RATIO
            // Nothing declared: under WPT capture the calibrated grid the
            // capture's own text lays on (1.25 × font — the ref pin);
            // outside it null, so the resolver keeps the historical 1.2
            // fallback and the frozen corpus is byte-identical.
            LineHeightNormal.LineBoxSource.CALIBRATED ->
                if (wptCapture) fontSizePx * REF_DEFAULT_FONT_LINE_HEIGHT_RATIO
                else null
        }
}
