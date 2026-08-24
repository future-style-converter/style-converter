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
    static func contentHeight(fontSizePx: CGFloat, design: Font.Design,
                              faceName: String? = nil) -> CGFloat {
        // wave-47 lane Z4 (SEAM 2 of the monospace pin) — a document
        // @font-face renders the label (PlaceholderLabel's fontFaceName
        // branch outranks both Inter and the generic designs), so ITS
        // ascent+|descent| is the content area the CSS leading splits
        // around. Probing the design instead left the split on SF Mono's
        // 1.178em while the pinned DejaVu (1.164em) painted — part of the
        // wave-46 Y7 glyphs-sit-low regression on hyphens-auto-inline-010.
        // Same UIFont probe as the Inter branch below; an unresolvable name
        // (face-free document, cleared registry, unit-test bundle) falls
        // through to the pre-wave-47 answer byte-for-byte.
        if let name = faceName, let f = UIFont(name: name, size: fontSizePx) {
            return f.lineHeight
        }
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
                        design: Font.Design,
                        faceName: String? = nil) -> (spacing: CGFloat, halfLeading: CGFloat) {
        // No declared line-height → natural metrics, nothing to add.
        guard let lh = lineHeightPx else { return (0, 0) }
        // Leading = line box height − content area height (§10.8.1); the
        // content area is the RENDERED face's — the document face when the
        // label paints one (wave-47 Z4, see contentHeight).
        let leading = lh - contentHeight(fontSizePx: fontSizePx, design: design,
                                         faceName: faceName)
        guard leading > 0 else { return (0, 0) }
        return (leading, leading / 2)
    }

    // MARK: - Wave 30 (lane LINEBOX) — the composed-WPT LINE-BOX PIN

    /// The rendered line count of a DISPLAY string: hard `\n` breaks + 1.
    ///
    /// For a run the greedy pre-break rewrote (PlaceholderLabel's
    /// `broken.preBroken`) this IS TextKit's line count — the breaker only
    /// emits lines that fit the measured `avail`, and TextKit's push-out
    /// strategy relocates SOFT breaks only. For any other run it is a floor,
    /// which is why [pinnedBoxHeight] refuses to pin those.
    /// - Parameter clampLimit: `line-clamp`'s line cap (css-overflow-4 §5)
    ///   when one is configured. A clamped block generates AT MOST that many
    ///   line boxes — the rest are not laid out at all — so the pin must
    ///   count the clamp, not the string. Measured: without this cap
    ///   css-overflow/line-clamp/block-ellipsis-001 (a 4-line run clamped to
    ///   2) pinned a 4-line box and lost 0.039 SSIM against its web pair.
    static func renderedLineCount(_ displayText: String,
                                  clampLimit: Int? = nil) -> Int {
        // reduce, not split(): an empty trailing line ("a\n") still counts,
        // exactly like a browser's empty last line box (CSS 2.1 §9.4.2).
        let hard = displayText.reduce(1) { $1 == "\n" ? $0 + 1 : $0 }
        // A clamp only ever REMOVES line boxes (a 2-line clamp on a 1-line
        // run still renders one), hence min, and a non-positive limit is
        // treated as "no clamp" — the same guard LineClampApplier applies.
        guard let cap = clampLimit, cap > 0 else { return hard }
        return min(hard, cap)
    }

    /// The height a text box must OCCUPY when the declared/calibrated line
    /// box — not the rendered face's natural metrics — drives block advance:
    /// `lineCount × line-height`, or nil to keep today's natural height.
    ///
    /// ## Why this exists (wave-30 diagnosis A6/B7)
    /// CSS 2.1 §10.8 makes a block's height the SUM OF ITS LINE BOXES, each
    /// exactly `line-height` tall; the face's ascent/descent choose where the
    /// glyphs sit INSIDE a line box, never how far down the next block
    /// starts. SwiftUI has no line-box concept: a `Text`'s reported height is
    /// whatever the face measures, ROUNDED UP by the platform's text engine,
    /// and [leading]'s `.padding(.vertical:)` then adds the CSS leading ON
    /// TOP of that already-rounded number. On iOS (where `UIFont.lineHeight`
    /// is the raw unrounded ascent+descent, 19.359375pt for Inter @16 —
    /// (1984 + 494) / 2048 × 16) the rounding and the leading are BOTH ~0.64
    /// and the box came out `L + leading` instead of `L`:
    ///
    ///     box = ceil(19.359375) + (20 − 19.359375) = 20.640625   (want 20)
    ///
    /// Device-measured on the composed WPT canvas (a `<p>` root = box + the
    /// UA 16px collapsed block margin, UABlockMargin): iOS advanced 36.64px
    /// per paragraph where Chromium, web and Compose all advance exactly
    /// 36 — the whole of iOS's 0.8985 residual on
    /// selectors/child-indexed-no-parent, which stacks 9 of them. Compose
    /// never had the defect: it hands Compose's `TextStyle.lineHeight` the
    /// declared box directly (LineHeightNormal.lineBoxSource /
    /// composedDefaultLineHeightPx), and Compose's line box IS that number.
    /// This is the iOS twin of that: pin the frame, so the platform's
    /// text-height rounding can never leak into the block advance.
    ///
    /// Mac Catalyst — the ONLY surface the unit tests can run on — CANNOT
    /// see the defect: `UIFont.lineHeight` there is already integral (20.0
    /// for Inter @16, macOS rounds ascent/descent), so `leading` is 0 and
    /// `ceil` is identity. That is why the pins below are arithmetic
    /// (LineBoxPinTests) plus a Catalyst raster case built on a DECLARED
    /// line-height, where the leading is non-zero on both platforms.
    ///
    /// - Parameters:
    ///   - lineHeightPx: the used line box (ComponentRenderer
    ///     .effectiveLineHeight — nil = `normal`/natural metrics, which has
    ///     no declared box to pin to).
    ///   - lineCount: [renderedLineCount] of the string actually rendered.
    ///   - lineCountIsExact: the caller KNOWS TextKit cannot add lines
    ///     (pre-broken, un-wrappable, or `white-space: nowrap`). A guessed
    ///     count must not pin: an under-count would make the box shorter
    ///     than the text and every following sibling would ride up.
    ///   - wptCapture: composed-WPT capture only — the product renderer and
    ///     the committed 327-pair baseline corpus keep the natural-height
    ///     box byte-for-byte, the same scoping rule Compose states in
    ///     LineHeightNormal.lineBoxSource for its own corrected arm.
    static func pinnedBoxHeight(lineHeightPx: CGFloat?,
                                lineCount: Int,
                                lineCountIsExact: Bool,
                                wptCapture: Bool) -> CGFloat? {
        // Product path: unchanged (see the scoping note above).
        guard wptCapture else { return nil }
        // `normal`/undeclared: there is no authored box — natural metrics
        // are the correct answer, and pinning would invent one.
        guard let lh = lineHeightPx, lh > 0 else { return nil }
        // A guessed line count is worse than no pin (see `lineCountIsExact`).
        guard lineCountIsExact, lineCount >= 1 else { return nil }
        // §10.8: N line boxes of exactly `line-height` each.
        return lh * CGFloat(lineCount)
    }

    /// Applier campaign (sub-natural line-height placement) — the
    /// compensating glyph-run translation for `line-height` BELOW the
    /// natural content height (e.g. `line-height: 1` on Inter, whose
    /// content area is ~1.21em). CSS half-leading is signed (CSS 2.1
    /// §10.8.1 / css-inline-3 §4.2): a browser centres the glyph band
    /// in an L-tall line box, so when L < contentHeight the band paints
    /// (L − contentHeight)/2 px ABOVE the line-box top (pixel-verified
    /// web model: inkTop = 8 + (L − natural)/2 across all 9 line-height
    /// captures). SwiftUI's `.lineSpacing` cannot go negative, so
    /// `leading` above clamps the line BOX at natural height — this
    /// offset restores the PLACEMENT half of the model: PlaceholderLabel
    /// applies it as a pure render-time `.offset(y:)` on the glyph run
    /// (layout-neutral, so the uncompressed box is untouched). Returns
    /// the SIGNED shift — negative (upward) in the sub-natural case,
    /// exactly 0 otherwise so every L ≥ natural render is byte-stable.
    /// Multi-line advance stays uncompressed (honest limitation — the
    /// caller logs it once). Pure — pinned by LineHeightWireTests.
    static func subNaturalOffset(lineHeightPx: CGFloat?,
                                 fontSizePx: CGFloat,
                                 design: Font.Design,
                                 faceName: String? = nil) -> CGFloat {
        // No declared line-height → natural metrics, no shift.
        guard let lh = lineHeightPx else { return 0 }
        // Signed half-leading: (L − contentHeight) / 2 (§10.8.1), against the
        // RENDERED face's content area (document face first — wave-47 Z4).
        let half = (lh - contentHeight(fontSizePx: fontSizePx, design: design,
                                       faceName: faceName)) / 2
        // Only the sub-natural (negative) case shifts — the positive
        // case is already handled by `leading`'s padding + lineSpacing.
        return half < 0 ? half : 0
    }
}
