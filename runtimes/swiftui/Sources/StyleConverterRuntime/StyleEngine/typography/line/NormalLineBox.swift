//
//  NormalLineBox.swift
//  StyleEngine/typography/line — wave 55 (lane L1, unit NB-ios).
//
//  CSS 2.1 §10.8.1: `line-height: normal` "tells user agents to set the used
//  value to a 'reasonable' value based on the font of the element" — the
//  number is UA-defined, so a composed-WPT capture must reproduce the UA the
//  frozen refs were rendered by (Chromium). CSS 2.1 §15.8: `font: 16px
//  monospace` resets `line-height` to `normal`, which the wire carries as
//  `LineHeight {"multiplier":1.2,"original":"normal"}` (LineHeightNormal).
//
//  THE DEFECT, measured on the wave54-final pictures (hyphenate-character-
//  001…004 ios). Under WPT capture a declared-`normal` run resolves to
//  `.natural` (LineHeightNormal.lineBoxSource), i.e. NO line box at all: the
//  run lays out on the face's RAW metrics — UIFont.lineHeight, 18.625 px per
//  line for DejaVu Sans Mono 16 ((1901 + 483) / 2048 × 16) — unpinned, and
//  SwiftUI ceils the N-line Text (94 / 75 / 56 px for 5 / 4 / 3 lines).
//  Chromium (the refs) and Compose (FontMetricsInt) lay `normal` on ROUNDED
//  metrics instead: round(14.85) + round(3.77) + round(0) = 19 px per line, so
//  their boxes are 95 / 76 / 57 and every iOS group from the second one on
//  sat one to two rows HIGH (the old 20-px `<br>` used to cancel that).
//
//  BLINK'S RULE (third_party/blink/renderer/platform/fonts/simple_font_data.cc,
//  SimpleFontData::PlatformInit, cited from memory — no Chromium checkout on
//  the capture host): line spacing = lroundf(ascent) + lroundf(descent) +
//  lroundf(lineGap), each term rounded ALONE, half away from zero. Measured,
//  not assumed: the ref `text-decoration-dotted-001` (Arial 92, hhea 1854 /
//  −434 / 67) advances 105 px per div where rounding the SUM would give 106,
//  and Compose agrees on three faces (wave55-plan/hyphen-ios-linebox.md §2).
//
//  THE FIX: hand that L to the existing wave-30 pin (LineBoxMetrics
//  .pinnedBoxHeight — CSS 2.1 §10.8, N line boxes of exactly L) for a run
//  whose line count is exact. One call site in PlaceholderLabel (seam 1);
//  every declared / calibrated run receives its `effective` value VERBATIM.
//

// SwiftUI: Font.Design names the generic family PlaceholderLabel renders
// (and brings UIKit's UIFont, the face probe LineBoxMetrics also uses).
import SwiftUI

/// Blink's rounded `line-height: normal` box for a pinned composed-WPT run.
// enum: a pure namespace of statics — never instantiated (as LineBoxMetrics).
enum NormalLineBox {

    /// Inter 4.0 hhea ascender over unitsPerEm (1984 / 2048) — the bundled
    /// default face's fallback when it is not registered (the unit-test
    /// bundle), the same table LineBoxMetrics.interContentRatio sums.
    static let interAscenderRatio: CGFloat = 1984.0 / 2048.0

    /// Inter 4.0 hhea descender over unitsPerEm (−494 / 2048) — signed like
    /// UIFont.descender, so ascender − descender is the content area.
    static let interDescenderRatio: CGFloat = -494.0 / 2048.0

    /// The log-once key for a Blink box below the face's raw line height.
    static let subNaturalLogKey = "line-height:normal-blink-below-raw"

    /// Blink's line spacing from ONE face's metrics: each term rounded alone,
    /// half away from zero (lroundf), then summed. `descender` is signed the
    /// UIFont way (negative below the baseline), so its magnitude is rounded.
    static func blinkLineSpacing(ascender: CGFloat, descender: CGFloat,
                                 leading: CGFloat) -> CGFloat {
        // lroundf(ascent): 14.8516 → 15 for DejaVu 16, 89.125 → 89 for Inter 92.
        let a = ascender.rounded(.toNearestOrAwayFromZero)
        // lroundf(descent) on the magnitude: 3.7734 → 4, so 15 + 4 = 19.
        let d = (-descender).rounded(.toNearestOrAwayFromZero)
        // lroundf(lineGap): Arial 92's 3.0098 → 3 (105, not the sum's 106).
        let g = leading.rounded(.toNearestOrAwayFromZero)
        // The three rounded terms ARE the line box (no rounding of the sum).
        return a + d + g
    }

    /// The RENDERED face's (ascender, descender, leading), resolved in exactly
    /// LineBoxMetrics.contentHeight's order so the box and the leading split
    /// it feeds measure the same face (NormalLineBoxTests P6 pins the order).
    static func faceMetrics(fontSizePx: CGFloat, design: Font.Design,
                            faceName: String? = nil)
        -> (ascender: CGFloat, descender: CGFloat, leading: CGFloat) {
        // 1. A document @font-face (wave-47 Z4) outranks every design: the
        //    monospace pin (DejaVu Sans Mono == Menlo metrics) rides here.
        if let name = faceName, let f = UIFont(name: name, size: fontSizePx) {
            // UIFont's own hhea-derived numbers — what TextKit lays out with.
            return (f.ascender, f.descender, f.leading)
        }
        // 2. The default design paints the bundled Inter.
        if design == .default {
            // Live probe of the registered face (the harness process).
            if let f = UIFont(name: "Inter", size: fontSizePx) {
                // The same three numbers UIFont.lineHeight is built from.
                return (f.ascender, f.descender, f.leading)
            }
            // Face missing (unit-test bundle) → Inter's hhea table, scaled;
            // line gap 0, matching LineBoxMetrics' interContentRatio.
            return (fontSizePx * interAscenderRatio,
                    fontSizePx * interDescenderRatio, 0)
        }
        // 3. Any other generic design measures the system face, as
        //    LineBoxMetrics.contentHeight does (its stated simplification).
        let f = UIFont.systemFont(ofSize: fontSizePx)
        // System face metrics, same accessor triple as the branches above.
        return (f.ascender, f.descender, f.leading)
    }

    /// The line-height PlaceholderLabel lays a run out on (seam 1): the pin's
    /// L and the leading split's input.
    ///
    /// - `effective` non-nil (every declared number and the WPT calibration):
    ///   returned VERBATIM — those runs are byte-identical by construction.
    /// - `effective` nil: Blink's rounded box iff ALL four hold —
    ///   * `declaredNormal`: the author's `normal` (an undeclared run is the
    ///     calibrated row, never nil under capture);
    ///   * `wptCapture`: the product path and the committed 327-pair
    ///     baselines keep natural metrics (LineHeightNormal.lineBoxSource);
    ///   * `lineCountIsExact`: only a PINNED box may take L — unpinned, the
    ///     leading would ride ON TOP of SwiftUI's ceiled natural height (the
    ///     wave-30 "box = ceil(raw) + leading" defect, LineBoxMetrics:138);
    ///   * `horizontalWritingMode`: css-writing-modes-4 §7 — in a vertical
    ///     mode the line box is an inline-axis column, so the horizontal
    ///     line-count proxy says nothing about it (plan D22:
    ///     hyphens-vertical-001's space-less `hyphen\u{AD}ation` reads as one
    ///     line while TextKit draws three; the pin would clamp it to 19 px).
    /// - otherwise nil: today's natural metrics, unchanged.
    static func usedLineHeight(effective: CGFloat?,
                               declaredNormal: Bool,
                               lineCountIsExact: Bool,
                               wptCapture: Bool,
                               horizontalWritingMode: Bool,
                               fontSizePx: CGFloat,
                               design: Font.Design,
                               faceName: String? = nil) -> CGFloat? {
        // A declared or calibrated box always wins (author > UA default).
        if let effective { return effective }
        // Every guard above must hold, or the run keeps natural metrics.
        guard declaredNormal, wptCapture, lineCountIsExact, horizontalWritingMode
        else { return nil }
        // The rendered face, resolved like the leading split's content area.
        let m = faceMetrics(fontSizePx: fontSizePx, design: design, faceName: faceName)
        // Blink's rounded line spacing for that face.
        let box = blinkLineSpacing(ascender: m.ascender, descender: m.descender,
                                   leading: m.leading)
        // A degenerate face (size 0) has no box to pin: keep today's path.
        guard box > 0 else { return nil }
        // The raw line height TextKit advances by (UIFont.lineHeight's sum).
        let raw = m.ascender - m.descender + m.leading
        // No silent fallthrough: below raw (Libertine 36: 41 < 41.04, Inter
        // 92: 111 < 111.3164) the pin still sizes the box, but SwiftUI's
        // `.lineSpacing` cannot be negative, so a MULTI-line run keeps the
        // raw advance inside it — said once per process.
        if box < raw {
            // The PropertyTracker breadcrumb (once per key per process).
            PropertyTracker.logOnce(
                key: subNaturalLogKey,
                message: "line-height: normal (WPT capture): Blink's rounded "
                    + "line box is below the face's raw line height; a "
                    + "multi-line run keeps the raw advance inside the pinned "
                    + "box (SwiftUI lineSpacing cannot be negative)")
        }
        // The pinned line box for this run.
        return box
    }
}
