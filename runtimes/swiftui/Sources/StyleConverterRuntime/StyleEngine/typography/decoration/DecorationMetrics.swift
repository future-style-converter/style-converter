//
//  DecorationMetrics.swift
//  StyleEngine/typography/decoration — lane IOS wave 5 (finding 4),
//  extended by the wave-5 gate follow-up (decoration-line ownership;
//  formerly OverlineMetrics.swift, renamed when it grew all 3 lines).
//
//  Per-line geometry for ALL THREE `text-decoration-line` values
//  (css-text-decor-3 §2.1: underline, overline, line-through — each
//  line box gets its own decoration). SwiftUI Text has no overline API
//  and its built-in `.underline()`/`.strikethrough()` draw ~1px lines
//  at platform offsets that the wave-5 device gate measured diverging
//  from Chromium (web ~2px at 22px Inter vs native 1px: Underline pair
//  0.809, UnderOver 0.740, Triple 0.737 against the fixture's 0.8695
//  no-decoration floor). The native runtime therefore OWNS the lines:
//  one Rectangle per rendered line per declared decoration, with
//  geometry matched to the archived Chromium captures.
//
//  THE GEOMETRY ORACLE IS EMPIRICAL — measured off the wave-5 gate web
//  captures (typography/text-decoration-line, 22px Inter, two wrapped
//  lines; card content top y=20, glyph ink rows 24..40, baseline y=41,
//  line advance 26px):
//    • 001_Underline:    solid 2px band rows 43-44  → top = baseline + 2
//    • 002_Overline:     solid 2px band rows 18-19  → bottom = 20
//                        = baseline − ascent (the line-box top)
//    • 003_LineThrough:  solid 2px band rows 33-34  → top = baseline − 8
//    • 005/006 combos repeat the same rows; the second line's overline
//      (rows 44-45) and line-through (rows 59-60) confirm the 26px
//      advance — decorations track each line box, not the block.
//  Expressed as font-relative fractions (F = font-size, A = ascent) so
//  the same rules scale to any size while reproducing the 22px rows:
//    thickness        = max(1, round(F/11))        → 2 @22 (2px bands),
//                       1 @16 (Chrome's classic 1px default underline)
//    underline top    = round(A + F·2/22)          → 41 + 2 = 43 @22
//    line-through top = round(A − F·8/22)          → 41 − 8 = 33 @22
//    overline top     = round(line top) − thickness → 20 − 2 = 18 @22
//      (the overline hangs ABOVE the line-box top — its bottom edge
//      sits at baseline − ascent, i.e. it paints into the padding band
//      exactly like Chromium's ink-overflowing decoration paint).
//
//  The per-line frame comes from the greedy lane: PlaceholderLabel's
//  display string is pre-broken into hard lines (GreedyLineBreaker) and
//  each line's inked advance comes from the SAME TextKit measurer the
//  fit test used — so the renderer overlays rects without a layout
//  callback. Single-line labels are the degenerate one-line case of the
//  same math. Empty lines (zero inked extent) paint nothing — the
//  browser draws no decoration over nothing.
//
//  Pure over an injected measurer + injected ascent so the XCTest suite
//  pins the geometry without any registered font (GreedyLineBreaker
//  test pattern).
//

// CoreGraphics CGFloat only — no view code in this file.
import CoreGraphics

enum DecorationMetrics {

    /// One decoration rect frame in the TEXT view's own coordinate
    /// space (origin at the first line's top-leading corner, before any
    /// padding/frame modifiers the label chains on afterwards). The
    /// same segment serves all three decoration kinds — only the y
    /// offset differs (the *Top functions below).
    struct Segment: Equatable {
        /// Line index — the caller derives the line top as
        /// `index × lineAdvance` (advance = content line height + CSS
        /// inter-line spacing, exactly what `.lineSpacing` makes
        /// TextKit lay out).
        let index: Int
        /// The line's inked width — the measurer's intrinsic advance,
        /// the same number the greedy fit test compared against.
        let width: CGFloat
        /// Decoration thickness (the empirical `auto` rule below).
        let thickness: CGFloat
    }

    /// `text-decoration-thickness: auto`, Chromium-matched: round(F/11)
    /// floored at 1px. Oracle: the 22px captures show 2px bands
    /// (22/11 = 2 exactly) and Chrome's classic default underline is
    /// 1px at 16px (round(16/11) = 1). Replaces the wave-5 `F/16`
    /// guess, which produced the very 1px-at-22px divergence the
    /// device gate measured.
    static func autoThickness(fontSizePx: CGFloat) -> CGFloat {
        max(1, (fontSizePx / 11).rounded())
    }

    /// Underline top edge relative to the LINE-BOX TOP: the band starts
    /// round(F·2/22) below the baseline (which sits `ascentPx` under
    /// the line top). Oracle: rows 43-44 with baseline 41 @F=22 → gap
    /// 2. Rounded to land on whole pixel rows like Blink's snapped
    /// decoration painter.
    static func underlineTop(ascentPx: CGFloat, fontSizePx: CGFloat) -> CGFloat {
        (ascentPx + fontSizePx * 2 / 22).rounded()
    }

    /// Line-through top edge relative to the LINE-BOX TOP: the band
    /// starts round(F·8/22) above the baseline. Oracle: rows 33-34 with
    /// baseline 41 @F=22 → raise 8 (the strike sits just above half of
    /// Inter's x-height, where Chromium centres it).
    static func lineThroughTop(ascentPx: CGFloat, fontSizePx: CGFloat) -> CGFloat {
        (ascentPx - fontSizePx * 8 / 22).rounded()
    }

    /// Overline top edge relative to the LINE-BOX TOP: the band's
    /// BOTTOM sits at the line top (baseline − ascent), so its top is
    /// one thickness ABOVE it — negative for the first line, hanging
    /// into the space above like Chromium's ink-overflow paint.
    /// Oracle: rows 18-19 with line top 20 @F=22 → top = −2.
    /// (Wave-5 drew the band DOWN from the line top; the capture
    /// measurement corrected the direction.)
    static func overlineTop(fontSizePx: CGFloat) -> CGFloat {
        -autoThickness(fontSizePx: fontSizePx)
    }

    /// Wave 22 (lane DECOR, B-RC4b) — the per-KIND row for one visual
    /// line, relative to the LINE-BOX TOP. This is the iOS twin of the
    /// Compose runtime's DecorationColorOps.bandTop (which is
    /// baseline-anchored, because Compose reads TextLayoutResult
    /// baselines while this overlay only knows line-box tops).
    ///
    /// It is a pure DISPATCH over the four rules that already existed —
    /// the wave-5 capture-pinned auto rows above and the wave-21
    /// ref-pinned explicit-thickness rows in DecorationOps — lifted out
    /// of the overlay's hardcoded `if overline / if strike / if underline`
    /// chain so the painter can walk an ORDERED, per-line-coloured
    /// request list instead (css-text-decor-3 §2.2). Every branch returns
    /// exactly what the overlay computed inline before, so the
    /// dark-stage 327 captures cannot move.
    ///
    /// `explicitThicknessPx` nil = `text-decoration-thickness: auto`
    /// (css-text-decor-4 §2.4 initial) → the font-derived rules.
    static func top(kind: DecorationColorOps.LineKind,
                    ascentPx: CGFloat,
                    fontSizePx: CGFloat,
                    explicitThicknessPx: CGFloat?) -> CGFloat {
        switch kind {
        case .overline:
            // Band BOTTOM flush with the line-box top; explicit swaps T in.
            return explicitThicknessPx.map { DecorationOps.explicitOverlineTop(thicknessPx: $0) }
                ?? overlineTop(fontSizePx: fontSizePx)
        case .lineThrough:
            // Strike CENTER is thickness-independent; the band straddles it.
            return explicitThicknessPx.map {
                DecorationOps.explicitLineThroughTop(ascentPx: ascentPx,
                                                     fontSizePx: fontSizePx,
                                                     thicknessPx: $0)
            } ?? lineThroughTop(ascentPx: ascentPx, fontSizePx: fontSizePx)
        case .underline:
            // Gap under the snapped baseline: auto = the measured F·2/22
            // rule, explicit = Blink's ref-pinned max(1, ceil(T/2)).
            return explicitThicknessPx.map {
                DecorationOps.explicitUnderlineTop(ascentPx: ascentPx, thicknessPx: $0)
            } ?? underlineTop(ascentPx: ascentPx, fontSizePx: fontSizePx)
        }
    }

    /// Segments for a display string already hard-broken into rendered
    /// lines (the label's pre-broken `\n` runs; a non-pre-broken label
    /// is a single line — the same measurer covers it, per the lane
    /// prescription). Lines with zero inked extent are skipped.
    ///
    /// Wave 21 (lane TEXTDECOR, B-RC8): `explicitThicknessPx` carries a
    /// declared `text-decoration-thickness` (css-text-decor-4 §2.4) —
    /// it overrides the auto rule; nil (the default, so every legacy
    /// call site and its pinned tests stay byte-identical) keeps the
    /// font-derived auto thickness above.
    static func segments(lines: [String],
                         fontSizePx: CGFloat,
                         explicitThicknessPx: CGFloat? = nil,
                         measure: (String) -> CGFloat) -> [Segment] {
        // One shared thickness per run — CSS resolves it per element;
        // an explicit declaration wins over the auto rule.
        let t = explicitThicknessPx ?? autoThickness(fontSizePx: fontSizePx)
        return lines.enumerated().compactMap { i, line in
            // Empty visual line → no decoration (browser parity).
            let w = line.isEmpty ? 0 : measure(line)
            return w > 0 ? Segment(index: i, width: w, thickness: t) : nil
        }
    }
}
