//
//  VerticalTextFlow.swift
//  StyleEngine/typography/writing — wave 35 lane B5, the VERTICAL-FLOW
//  decision module.
//
//  ## What this file is
//  The pure, platform-free half of vertical text layout: given a
//  `writing-mode`, a `text-orientation` and the run's string, it answers the
//  three questions a renderer has to ask before it can typeset a vertical
//  line, and nothing else. No SwiftUI types appear here on purpose — the
//  Compose twin
//  (runtimes/compose/src/main/java/com/styleconverter/runtime/typography/
//   text/VerticalTextFlow.kt) is the same file in Kotlin, function for
//  function, so the two natives can never disagree about WHICH runs are
//  upright or WHERE a vertical line breaks.
//
//    1. `runOrientation` — css-writing-modes-4 §5.1 `text-orientation`: do
//       this run's glyphs stand UPRIGHT in the vertical line, or are they
//       typeset ROTATED 90° (the "sideways" family)? `.mixed` = the run needs
//       both and this slice declines it.
//    2. `lineStack` — css-writing-modes-4 §3: which way do successive LINES
//       stack? `vertical-rl` / `sideways-rl` stack right-to-left, the two
//       `-lr` modes left-to-right.
//    3. `uprightColumns` — the line-breaking plan for an UPRIGHT run: the
//       inline axis is VERTICAL, so the available "width" a line wraps at is
//       the box's HEIGHT budget, and each glyph advances by its own vertical
//       advance.
//
//  ## Why the upright case needs its own path at all
//  Measured on the frozen wave34-final css-writing-modes capture
//  (available-size-011, the WPT "ＰＡＳＳ" test): iOS has NO vertical text
//  rendering at all and paints the run horizontally in source order
//  ("Ｓ Ｓ Ａ Ｐ", 0.9415), while Android runs it through the wave-5
//  rotated-run branch and gets the right LINE ORDER with every glyph lying on
//  its side (0.9247). Web renders it upright at 0.9898. The characters are
//  FULLWIDTH Latin (U+FF30 Ｐ, U+FF21 Ａ, U+FF33 Ｓ), whose Unicode
//  Vertical_Orientation is U, so `text-orientation: mixed` (the initial
//  value) sets them UPRIGHT. A whole-run rotation can never express that:
//  the glyphs and the line stacking need opposite transforms.
//
//  ## Blast radius, enumerated before the change
//  Replaying `runOrientation` over every component in the 29 frozen
//  wave34-final sections that carries a vertical `writing-mode` AND its own
//  text yields exactly ONE upright run — `available-size-011__1__0`. The
//  other 17 are ASCII (`"0"`, `"0 0 0 0 0 0 0"`, css-text-decor's
//  `"ABC ABC"`), all Vertical_Orientation R ⇒ `.rotated` ⇒ the caller keeps
//  its frozen branch verbatim. Everything outside a vertical writing mode
//  never reaches this file.
//

import Foundation

/// The five `writing-mode` keywords (css-writing-modes-4 §3).
///
/// `WritingModeConfig` only ever recorded a boolean `isVertical`, which
/// cannot tell `vertical-rl` from `vertical-lr` — and the line-stacking side
/// is exactly that difference. Spelled here so the enum lives beside the
/// decisions that consume it, mirroring the Compose twin's
/// `WritingModeValue`.
enum WritingModeValue: String, Equatable {
    case horizontalTb
    case verticalRl
    case verticalLr
    case sidewaysRl
    case sidewaysLr

    /// Parse an IR keyword (`"vertical-rl"`, `"VERTICAL_RL"`, …).
    /// Unknown keywords answer `.horizontalTb` — the initial value, and the
    /// one that keeps every caller on its horizontal path.
    static func from(keyword: String?) -> WritingModeValue {
        switch (keyword ?? "").lowercased().replacingOccurrences(of: "_", with: "-") {
        case "vertical-rl": return .verticalRl
        case "vertical-lr": return .verticalLr
        case "sideways-rl": return .sidewaysRl
        case "sideways-lr": return .sidewaysLr
        default: return .horizontalTb
        }
    }
}

/// The three `text-orientation` keywords (css-writing-modes-4 §5.1).
enum TextOrientationValue: String, Equatable {
    case mixed
    case upright
    case sideways

    /// Parse an IR keyword; unknown answers `.mixed` (the initial value).
    static func from(keyword: String?) -> TextOrientationValue {
        switch (keyword ?? "").lowercased() {
        case "upright": return .upright
        case "sideways": return .sideways
        default: return .mixed
        }
    }
}

/// How the glyphs of ONE run stand inside a vertical line
/// (css-writing-modes-4 §5.1).
enum GlyphOrientation: Equatable {
    /// Every glyph stands upright; the line advances downward.
    case upright
    /// Every glyph is typeset sideways (rotated 90° clockwise).
    case rotated
    /// The run mixes both classes. Real browsers typeset such a run
    /// per-character; this slice declines it and the caller keeps whatever it
    /// did before, rather than guessing (repo no-silent-fallthrough rule).
    case mixed
}

/// Which way successive LINES stack (css-writing-modes-4 §3).
enum LineStack: Equatable {
    /// `vertical-rl`, `sideways-rl`: line 1 is the RIGHTMOST column.
    case rightToLeft
    /// `vertical-lr`, `sideways-lr`: line 1 is the LEFTMOST column.
    case leftToRight
}

enum VerticalTextFlow {

    /// Hard ceiling on the number of vertical lines a plan may produce.
    ///
    /// A bounded plan is a plan we can reason about; an unbounded one is a
    /// per-glyph `Text` explosion in the renderer. 64 columns is ~3× the
    /// widest upright run any WPT reference page in the corpus draws, so no
    /// real document hits it — it only stops a degenerate budget (a 1pt
    /// height on a 500-character run) from building 500 leaf views.
    static let maxColumns: Int = 64

    /// Is this scalar's Unicode Vertical_Orientation `U` or `Tu` — i.e. does
    /// it stand UPRIGHT under `text-orientation: mixed`?
    ///
    /// This is a deliberately BOUNDED range table, not a full UCD import: the
    /// runtime ships no Unicode data files and the property has ~300 ranges.
    /// What is listed below is the U-class core — the CJK/Kana/Hangul blocks
    /// plus the fullwidth compatibility forms — which is exactly the set the
    /// WPT corpus exercises. Anything not listed answers `false` (the R
    /// class, "rotate me"), which is both the majority answer and the safe
    /// one: it keeps the caller on its frozen rotated branch.
    ///
    /// Ranges (UAX #50 Vertical_Orientation=U, abridged):
    ///  - U+1100–U+11FF  Hangul Jamo
    ///  - U+2E80–U+303E  CJK Radicals, Kangxi, CJK Symbols & Punctuation
    ///  - U+3041–U+33FF  Kana, Bopomofo, Hangul Compat Jamo, Kanbun,
    ///                   CJK Strokes, Enclosed CJK, CJK Compatibility
    ///  - U+3400–U+4DBF  CJK Unified Ideographs Extension A
    ///  - U+4E00–U+9FFF  CJK Unified Ideographs
    ///  - U+A000–U+A4CF  Yi Syllables + Radicals
    ///  - U+A960–U+A97F  Hangul Jamo Extended-A
    ///  - U+AC00–U+D7FF  Hangul Syllables + Jamo Extended-B
    ///  - U+F900–U+FAFF  CJK Compatibility Ideographs
    ///  - U+FE10–U+FE19  Vertical Forms
    ///  - U+FE30–U+FE4F  CJK Compatibility Forms
    ///  - U+FF01–U+FF60  FULLWIDTH ASCII variants ← the available-size-011 run
    ///  - U+FFE0–U+FFE6  Fullwidth signs
    ///  - U+20000–U+2FFFD / U+30000–U+3FFFD  CJK Extensions B…
    ///
    /// Deliberately NOT listed: U+FF61–U+FF9F (HALFWIDTH katakana), whose
    /// Vertical_Orientation is R — halfwidth forms rotate, which is the whole
    /// point of the halfwidth/fullwidth distinction in vertical typesetting.
    static func isUprightOrientation(_ codePoint: UInt32) -> Bool {
        switch codePoint {
        case 0x1100...0x11FF: return true
        case 0x2E80...0x303E: return true
        case 0x3041...0x33FF: return true
        case 0x3400...0x4DBF: return true
        case 0x4E00...0x9FFF: return true
        case 0xA000...0xA4CF: return true
        case 0xA960...0xA97F: return true
        case 0xAC00...0xD7FF: return true
        case 0xF900...0xFAFF: return true
        case 0xFE10...0xFE19: return true
        case 0xFE30...0xFE4F: return true
        case 0xFF01...0xFF60: return true
        case 0xFFE0...0xFFE6: return true
        case 0x20000...0x2FFFD: return true
        case 0x30000...0x3FFFD: return true
        default: return false
        }
    }

    /// The glyph orientation for one run, or `nil` when the mode is not
    /// vertical (nothing to decide — the caller's horizontal path owns it).
    ///
    /// css-writing-modes-4 §3 + §5.1:
    ///  - `sideways-rl` / `sideways-lr` typeset EVERYTHING sideways; the
    ///    `text-orientation` value does not apply (the mode already fixes it).
    ///  - `text-orientation: sideways` ⇒ `.rotated`, `upright` ⇒ `.upright` —
    ///    both unconditional, no per-character question.
    ///  - `text-orientation: mixed` (initial) ⇒ per-character
    ///    Vertical_Orientation: U/Tu stand upright, R/Tr rotate.
    ///
    /// WHITESPACE IS IGNORED when classifying: a space paints no glyph, so
    /// its R-class membership must not drag an otherwise all-upright CJK run
    /// into `.mixed`. (`"Ｓ Ｓ Ａ Ｐ"` is an upright run with three spaces in
    /// it, and Chrome typesets it upright.) A run that is ONLY whitespace has
    /// no glyphs to orient and answers `.rotated` — the frozen branch, i.e.
    /// no behaviour change.
    static func runOrientation(writingMode: WritingModeValue,
                               textOrientation: TextOrientationValue,
                               text: String) -> GlyphOrientation? {
        switch writingMode {
        // Horizontal flow — this module has no opinion.
        case .horizontalTb: return nil
        // §3: the sideways-* modes ARE "text-orientation: sideways".
        case .sidewaysRl, .sidewaysLr: return .rotated
        case .verticalRl, .verticalLr: break
        }
        switch textOrientation {
        case .upright: return .upright
        case .sideways: return .rotated
        case .mixed: return classifyMixed(text)
        }
    }

    /// §5.1 `mixed`: per-character Vertical_Orientation over the whole run.
    private static func classifyMixed(_ text: String) -> GlyphOrientation {
        var sawUpright = false
        var sawRotated = false
        for scalar in text.unicodeScalars {
            // Whitespace paints nothing — see the doc comment's note.
            if CharacterSet.whitespacesAndNewlines.contains(scalar) { continue }
            if isUprightOrientation(scalar.value) { sawUpright = true } else { sawRotated = true }
            // Both classes present: no single-transform answer exists.
            if sawUpright && sawRotated { return .mixed }
        }
        // All-upright wins only when at least one upright glyph was seen; a
        // glyphless (or all-R) run answers `.rotated` = the frozen branch.
        return sawUpright ? .upright : .rotated
    }

    /// Which side line 1 sits on, or `nil` for a horizontal mode.
    ///
    /// css-writing-modes-4 §3: the block-progression direction of the two
    /// `-rl` modes is right-to-left, of the two `-lr` modes left-to-right.
    /// `direction` (the INLINE direction) does not enter — it orders glyphs
    /// within a line, not lines within a block.
    static func lineStack(_ writingMode: WritingModeValue) -> LineStack? {
        switch writingMode {
        case .horizontalTb: return nil
        case .verticalRl, .sidewaysRl: return .rightToLeft
        case .verticalLr, .sidewaysLr: return .leftToRight
        }
    }

    /// The run split into one string per UNICODE SCALAR — the renderer's
    /// per-glyph slot list, and the array `uprightColumnIndices` indexes into.
    ///
    /// Scalars, not Characters: it is the unit the Kotlin twin's `codePointAt`
    /// walk uses, so the two natives split identically. The shared limitation
    /// that buys is that a combining sequence could be split across two lines
    /// — no corpus run contains one (upright runs are CJK/kana/fullwidth, all
    /// single-scalar), and the alternative (one platform grouping graphemes
    /// and the other not) is exactly the silent twin drift the byte-parallel
    /// rule exists to prevent.
    static func codePointsOf(_ text: String) -> [String] {
        text.unicodeScalars.map { String($0) }
    }

    /// Break an UPRIGHT run into vertical lines, in LOGICAL order (element 0
    /// is the first line = the RIGHTMOST column under `.rightToLeft`).
    ///
    /// The inline axis of a vertical writing mode is the box's BLOCK axis on
    /// screen, so the wrap budget is the box's available HEIGHT and each
    /// glyph consumes its own vertical advance.
    ///
    /// - Parameters:
    ///   - glyphs: the run as per-scalar strings — exactly what `codePointsOf`
    ///     returns, and exactly the order the renderer built its glyph
    ///     subviews in, so the returned indices address them directly.
    ///   - charAdvancePx: one upright glyph's advance along the vertical
    ///     inline axis. Upright vertical typesetting gives every glyph the
    ///     same em-square advance (that is what "upright" means — the glyph
    ///     sits in its em box), so ONE number describes the run.
    ///   - budgetPx: the available inline extent, or `nil` when the caller's
    ///     proposal is unbounded.
    /// - Returns: per line, the indices into `glyphs` that line paints, or
    ///   `nil` to DECLINE — the caller must then keep its existing branch
    ///   untouched. Declines on: an unbounded or non-positive budget (we
    ///   refuse to invent a wrap width), a non-positive advance, a glyphless
    ///   run, or a plan wider than `maxColumns`.
    static func uprightColumnIndices(glyphs: [String],
                                     charAdvancePx: Double,
                                     budgetPx: Double?) -> [[Int]]? {
        // An unbounded inline axis is a real CSS state (`height: auto` with
        // no constraining ancestor) and its answer is "one very long line" —
        // but a renderer that guesses here would move pixels on documents
        // this slice has never measured, so we decline instead.
        guard let budgetPx, budgetPx.isFinite, budgetPx > 0 else { return nil }
        guard charAdvancePx.isFinite, charAdvancePx > 0 else { return nil }
        guard glyphs.contains(where: { !isCollapsibleSpace($0) }) else { return nil }

        // How many glyphs fit in one line. `+ 1e-3` absorbs the sub-pixel
        // rounding a measured advance carries (a 16.0000001pt advance in a
        // 16pt budget must still fit ONE glyph, not zero); floor() then gives
        // the browser's "as many as fully fit" rule. Never below 1 — CSS
        // always places at least one glyph per line, overflowing if it must.
        let capacity = max(1, Int((budgetPx / charAdvancePx) + 1e-3))

        var columns: [[Int]] = []
        var i = 0
        while i < glyphs.count {
            // css-text-3 §4.1.3: a space at a line break hangs / collapses —
            // it never starts the next line.
            while i < glyphs.count && isCollapsibleSpace(glyphs[i]) { i += 1 }
            if i >= glyphs.count { break }
            var line: [Int] = []
            while i < glyphs.count && line.count < capacity {
                line.append(i)
                i += 1
            }
            // …and a space that landed at the END of a line collapses too.
            while let last = line.last, isCollapsibleSpace(glyphs[last]) { line.removeLast() }
            if !line.isEmpty { columns.append(line) }
            if columns.count > maxColumns { return nil }
        }
        return columns.isEmpty ? nil : columns
    }

    /// `uprightColumnIndices` rendered back to strings — the shape the unit
    /// tests assert on, and the shape a debug log prints. Same decisions, one
    /// call away, so a test can never pin a plan the renderer does not use.
    static func uprightColumns(text: String,
                               charAdvancePx: Double,
                               budgetPx: Double?) -> [String]? {
        let glyphs = codePointsOf(text)
        guard let plan = uprightColumnIndices(glyphs: glyphs,
                                              charAdvancePx: charAdvancePx,
                                              budgetPx: budgetPx) else { return nil }
        return plan.map { col in col.map { glyphs[$0] }.joined() }
    }

    /// The two separators CSS collapses at a line break in normal wrapping.
    private static func isCollapsibleSpace(_ glyph: String) -> Bool {
        glyph == " " || glyph == "\t"
    }
}
