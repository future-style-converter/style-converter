//
//  LineBoxMetrics.swift
//  StyleEngine/typography — fidelity wave 3.
//
//  CSS line-box arithmetic (CSS 2.1 §10.8 / css-inline-3 §4.2): a used
//  `line-height: L` distributes its leading around the glyphs' CONTENT
//  AREA — half above, half below EVERY line, including the first and
//  last. SwiftUI's `.lineSpacing` only adds space BETWEEN lines, so an
//  auto-height text box came out ~one leading short and its glyph band
//  sat high (wave-3 measurement: IH_LineHeight 0.796, Images_TextBlock
//  0.892, Columns_TextBlock 0.903 — the remaining iOS text-metrics
//  channel after wave 1). PlaceholderLabel now:
//    • sets `.lineSpacing(L − contentHeight)` so the line ADVANCE is
//      exactly L (the browser's), and
//    • pads the glyph run by `(L − contentHeight) / 2` vertically so
//      the first/last half-leading exists like in a browser line box.
//
//  The content-area height is the rendered face's ascent + |descent|
//  (+ line gap, zero for Inter) — the same metrics Chromium uses for
//  `line-height: normal` boxes on macOS (hhea table). Probed live via
//  UIFont so the value tracks the exact font in the render; falls back
//  to Inter 4.0's hhea constants (ascender 1984, descender 494, em
//  2048 → 1.20996) when the face isn't registered — e.g. in the unit-
//  test bundle — keeping tests deterministic (same pattern as
//  TypographyExtractor.referenceMetricRatio).
//

import SwiftUI

enum LineBoxMetrics {

    /// Inter 4.0 hhea (ascender + |descender|) / unitsPerEm — the
    /// content-area-to-em ratio used when the bundled face can't be
    /// probed. (1984 + 494) / 2048.
    static let interContentRatio: CGFloat = 2478.0 / 2048.0

    /// Content-area height (ascent + |descent| + line gap) in px for
    /// the face PlaceholderLabel renders with: bundled Inter for the
    /// default design, the system font for generic-family signals
    /// (serif / monospaced / rounded map to SF variants whose vertical
    /// metrics match SF's).
    static func contentHeight(fontSizePx: CGFloat, design: Font.Design) -> CGFloat {
        if design == .default {
            // Live probe of the registered Inter face (harness app);
            // UIFont.lineHeight = ascender − descender (+ leading).
            if let f = UIFont(name: "Inter", size: fontSizePx) {
                return f.lineHeight
            }
            // Test bundle / face missing → Inter's table constants.
            return fontSizePx * interContentRatio
        }
        // Generic families ride the system face — its metrics are what
        // SwiftUI lays the Text out with.
        return UIFont.systemFont(ofSize: fontSizePx).lineHeight
    }

    /// The leading split for a used CSS line-height (CSS 2.1 §10.8.1):
    /// `spacing` is the extra advance BETWEEN lines (SwiftUI
    /// `.lineSpacing`), `halfLeading` the band added above the first
    /// and below the last line. Both zero when no line-height was
    /// declared (SwiftUI's natural metrics ARE `line-height: normal`)
    /// or when L ≤ contentHeight — negative leading (line-height below
    /// normal) is clamped: SwiftUI can't overlap lines, and the
    /// pre-wave-3 renderer already clamped there, keeping those
    /// fixtures byte-stable.
    static func leading(lineHeightPx: CGFloat?,
                        fontSizePx: CGFloat,
                        design: Font.Design) -> (spacing: CGFloat, halfLeading: CGFloat) {
        // No declared line-height → natural metrics, nothing to add.
        guard let lh = lineHeightPx else { return (0, 0) }
        // Leading = line box height − content area height (§10.8.1).
        let leading = lh - contentHeight(fontSizePx: fontSizePx, design: design)
        guard leading > 0 else { return (0, 0) }
        return (leading, leading / 2)
    }
}
