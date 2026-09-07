//
//  LhUnitLineHeight.swift
//  StyleEngine/spacing — wave 43, lane V3.
//
//  The `lh` unit's LINE-HEIGHT SOURCE. css-values-4 §6.1.1: `lh` is
//  "equal to the computed value of the line-height property of the
//  element on which it is used". Until this wave SpacingResolver (and the
//  calc evaluator) hardcoded `1.2 × font-size` — the UA default-sheet
//  approximation of `normal` — for EVERY element.
//
//  ## The defect, measured on the wave42-final gate
//  css-overflow/line-clamp/discard/discard-multicol-001/002/004 declare
//  `font-family: monospace; height: 2lh` and no line-height. iOS scored
//  0.8737/0.8734/0.8737 with the 2lh box resolved 2 × 1.2 × 13 = 31.2px
//  while the Chromium ref's content box is 33px — 2 × 13 × 1.25, the
//  corpus-v4.1 REF_LINE_HEIGHT ratio the ref injection pins and the SAME
//  calibrated grid every WPT capture's own text lays on
//  (ComponentRenderer.wptRefLineHeightRatio, consumed by
//  effectiveLineHeight / ListMarkerRow). The box therefore clips its
//  second text row mid-glyph on both natives.
//
//  ## The three-state contract (the shared LineHeightNormal table, mapped
//  onto a NUMERIC unit basis)
//  | line-height on the wire   | WPT capture             | elsewhere     |
//  |---------------------------|-------------------------|---------------|
//  | typed value (px-resolved) | that value, verbatim    | that value    |
//  | the `normal` keyword      | calibrated 1.25 × font  | nil → 1.2em   |
//  | absent                    | calibrated 1.25 × font  | nil → 1.2em   |
//  A returned nil tells the resolver "no source" and its lh arms keep the
//  historical 1.2 × font-size fallback — the dark-stage byte-stability
//  contract (no committed fixture under fixtures/properties|components
//  uses the lh unit; verified by grep at wave 43).
//
//  ## Two stated approximations, deliberate and documented
//  * `normal` under WPT capture maps to the CALIBRATED ratio, not the
//    face's natural metrics (LineHeightNormal's .natural row): a static
//    unit resolution needs a number and has no text-engine channel; the
//    pinned ref ratio is the deterministic stand-in the lane brief
//    directs, and no wave42-final lh consumer declares `normal` anyway.
//  * iOS keys the calibration on the single wptCaptureMode environment
//    flag — exactly the flag effectiveLineHeight's CALIBRATED row uses,
//    so the lh basis and the text grid agree on every iOS WPT capture.
//
//  Byte-parallel twin: Compose spacing/LhUnitLineHeight.kt. Web needs no
//  twin — it emits the lh length verbatim and the browser resolves it
//  against the real used line-height for free.
//

// CoreGraphics supplies CGFloat for the ratio constant's conversion.
import CoreGraphics
import Foundation

enum LhUnitLineHeight {

    /// The USED line-height (px) the `lh` unit resolves against, or nil
    /// when no source exists and the resolver must keep its historical
    /// 1.2 × font-size fallback (the dark-stage byte-stability contract).
    ///
    /// - Parameters:
    ///   - declaredPx: the element's DECLARED line-height after typography
    ///     extraction (TypographyAggregate.lineHeightPx — px lengths
    ///     verbatim, multipliers/percentages folded × font-size). Nil when
    ///     the wire carried no LineHeight. NOTE: for the `normal` keyword
    ///     the extractor DOES emit its legacy 1.2 compatibility number
    ///     here — `declaredNormal` is what demotes it.
    ///   - declaredNormal: did the cascade declare the `normal` KEYWORD
    ///     (TypographyAggregate.lineHeightIsNormal — the shared
    ///     LineHeightNormal predicate)? Then the number in `declaredPx` is
    ///     the wire's 1.2 stand-in, not an author value, and must not win
    ///     under capture.
    ///   - fontSizePx: the element's resolved font size in px — the SAME
    ///     value SpacingContext carries (declared, or the monospace-UA
    ///     13px quirk, or the 16px default), so lh and em can never
    ///     disagree about the font they measure.
    ///   - wptCapture: the `wptCaptureMode` environment flag at the
    ///     renderer's context fold — gates the calibrated rows exactly as
    ///     the table above states.
    static func usedLineHeightPx(declaredPx: Double?,
                                 declaredNormal: Bool,
                                 fontSizePx: Double,
                                 wptCapture: Bool) -> Double? {
        // Delegate the STATE pick to the shared native decision so this
        // table and the text-run tables (effectiveLineHeight, the marker
        // row) can never disagree about which declaration won.
        switch LineHeightNormal.lineBoxSource(hasDeclaredValue: declaredPx != nil,
                                              declaredNormal: declaredNormal,
                                              wptCapture: wptCapture) {
        // An author value wins outright (author > us), verbatim —
        // including an explicit `line-height: 0` (css-inline-3 §4.2
        // allows it; 2lh is then honestly 0). Force-unwrap is safe by
        // construction: `.declared` is only returned when
        // `hasDeclaredValue` was true above.
        case .declared:
            return declaredPx!
        // Declared `normal` under WPT capture — the calibrated ref ratio
        // as the documented numeric stand-in for the face metrics a
        // static resolve cannot look up (see the header).
        case .natural:
            return fontSizePx * Double(ComponentRenderer.wptRefLineHeightRatio)
        // Nothing declared: under WPT capture the calibrated grid the
        // capture's own text lays on (the ref's unitless 1.25, recomputed
        // per element font size); outside it nil, so the resolver keeps
        // the historical 1.2 fallback and the frozen corpus is
        // byte-identical.
        case .calibrated:
            return wptCapture
                ? fontSizePx * Double(ComponentRenderer.wptRefLineHeightRatio)
                : nil
        }
    }
}
