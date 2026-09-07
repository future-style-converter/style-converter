//
//  WordBreakOpportunities.swift
//  StyleEngine/typography/wrapping — wave 41 (lane T3).
//
//  The IN-WORD break opportunities the greedy pre-break was blind to.
//
//  GreedyLineBreaker splits on SPACES only, so to it `Deoxy\u{AD}ribo\u{AD}
//  nucleic` and `regu-lation` are single unbreakable "words". Both natives'
//  wave40-final css-text captures show what that costs on iOS, where the
//  pre-break IS the line breaker (TextKit only ever sees hard newlines):
//
//   * hyphens-manual-011/012/013 (0.8832/0.8832/0.8818): the fold commits
//     the shy-carrying word as ONE overflowing line, the line COUNT feeds
//     the pinned box height (2 lines), and TextKit then re-breaks the
//     overflowing line at the U+00AD it can see — 3 rendered lines
//     vertically centered in a 2-line frame, overflowing the border
//     symmetrically top and bottom.
//   * hyphens-none-012/013 (0.8548/0.8550): same undercount for words
//     carrying REAL hyphens (`regu-lation` → 3 committed lines, 6 rendered).
//
//  This file models exactly those two opportunity classes, per css-text-3:
//
//   * U+00AD SOFT HYPHEN (§5.3) — a CONDITIONAL hyphenation opportunity:
//     invisible unless the line breaks there, in which case the UA paints
//     the hyphenate-character. `hyphens: none` never lets one reach this
//     code (SoftHyphenPolicy deletes them at the call site), so any shy
//     seen here is an opportunity by construction (`manual`/`auto`).
//   * U+002D HYPHEN-MINUS and U+2010 HYPHEN (UAX #14 class HY/BA) — plain
//     break-AFTER opportunities that `hyphens` does not govern (the
//     assertion of WPT css-text/hyphens-none-012: "'hyphens: none' does
//     not suppress line wrapping after … an actual hyphen-minus"). The
//     glyph is already in the text, so nothing is painted at the break.
//
//  En/em dashes, ZWSP and ideographs stay UNmodeled on purpose: the fold
//  leaves such words whole and `hasUnbreakableOverflowingLine`'s
//  `hasSoftWrapOpportunity` veto keeps rule B from firing on them — the
//  platform breaker then owns those breaks exactly as before this wave.
//
//  Twin: WordBreakOpportunities.kt (Compose), same model, same offsets.
//  Pure Foundation so XCTest pins it without a renderer or font.
//

import Foundation

enum WordBreakOpportunities {

    /// One in-word break opportunity, addressed in the CLEANED word.
    struct Op {
        /// UTF-16 offset into `Word.text` where the break lands: the head
        /// is `text[..<offset]`, the tail `text[offset...]`. Always > 0
        /// and < the word's UTF-16 length (a break needs ink both sides).
        let offset: Int
        /// true — a HYPHENATION point (a removed soft hyphen, or a
        /// dictionary point the caller merged in): taking it paints the
        /// hyphenate-character at the end of the head (§5.3). false — a
        /// literal-hyphen break-after point: the glyph is already there.
        let paintsHyphen: Bool
    }

    /// A space-split word, ready for the greedy fold: the DISPLAY text
    /// (soft hyphens removed — they have no advance unless taken, so this
    /// is also the correct MEASUREMENT string) plus its explicit ops.
    struct Word {
        let text: String
        let ops: [Op]
    }

    /// Decompose one raw space-split word: strip U+00AD (recording each
    /// position as a hyphenation op) and record a break-after op behind
    /// every U+002D / U+2010 whose successor may start a line.
    static func analyze(_ raw: String) -> Word {
        var text = String.UnicodeScalarView()
        var ops: [Op] = []
        var utf16Len = 0            // UTF-16 length of `text` so far.
        var pendingHyphen = false   // saw -/‐, op confirmed by successor.
        for scalar in raw.unicodeScalars {
            if scalar.value == 0xAD {
                // Soft hyphen: do NOT emit the scalar. Its position in the
                // cleaned word is a hyphenation opportunity — unless it is
                // word-initial (offset 0: no head to break off) or lands
                // where a literal hyphen already broke (duplicate offset:
                // the literal op wins, nothing extra must be painted).
                if utf16Len > 0, ops.last?.offset != utf16Len {
                    ops.append(Op(offset: utf16Len, paintsHyphen: true))
                }
                continue
            }
            // A pending literal-hyphen op is confirmed by its successor —
            // unless that successor is a digit (UAX #14 forbids HY × NU:
            // `1-2` ranges keep their halves together). A soft hyphen
            // recorded at the same offset (authored right after the
            // literal one) is REPLACED: the break lands in one place and
            // the literal glyph is already there, so nothing extra may be
            // painted.
            if pendingHyphen {
                if !(scalar.value >= 0x30 && scalar.value <= 0x39) {
                    if ops.last?.offset == utf16Len { ops.removeLast() }
                    ops.append(Op(offset: utf16Len, paintsHyphen: false))
                }
                pendingHyphen = false
            }
            text.append(scalar)
            utf16Len += UTF16.width(scalar)
            // Break-after candidate; confirmed above once we know there IS
            // a next scalar (a word-final hyphen has no tail to move).
            if scalar.value == 0x2D || scalar.value == 0x2010 {
                pendingHyphen = true
            }
        }
        return Word(text: String(text), ops: ops)
    }

    /// The remainder of `word` after a split at UTF-16 offset `offset`:
    /// the suffix string plus the surviving ops REBASED into it. Soft-
    /// hyphen positions cannot be recomputed from the suffix (the marks
    /// were removed by `analyze`), which is why rebase — not re-analyze —
    /// is the only correct tail constructor.
    static func tail(_ word: Word, splitAt offset: Int, suffix: String) -> Word {
        Word(text: suffix,
             ops: word.ops.filter { $0.offset > offset }
                          .map { Op(offset: $0.offset - offset, paintsHyphen: $0.paintsHyphen) })
    }

    /// The GREEDIEST split of `word` whose head — placed after `prefix`,
    /// plus the painted hyphen when the op asks for one — fits `maxWidth`:
    /// css-text-3 §5 breaks at the LAST opportunity that fits, which is
    /// what Chromium's and Minikin's breakers both do.
    ///
    /// `overflowFallback` — when no op fits AND the word opens its line
    /// (`prefix` empty), take the FIRST op anyway: CSS still breaks at the
    /// earliest opportunity to MINIMIZE the overflow (the Chromium ref for
    /// hyphens-none-012 wraps `imple-menta-tion` as `imple-`/`menta-`/
    /// `tion` even where `imple-` alone overflows the 6ch box). Callers
    /// pass true only for EXPLICIT ops — the wave-40 dictionary contract
    /// (no point fits ⇒ the whole word overflows) is preserved.
    static func split(_ word: Word,
                      ops: [Op],
                      after prefix: String,
                      maxWidth: CGFloat,
                      measure: (String) -> CGFloat,
                      hyphenChar: String,
                      overflowFallback: Bool) -> (head: String, tail: Word)? {
        // Walk DOWN from the last opportunity so the first fit found is
        // the greediest one (identical to the wave-40 dictionary walk).
        for op in ops.reversed() {
            guard let cut = pieces(word, at: op, hyphenChar: hyphenChar) else { continue }
            if measure(prefix + cut.head) <= maxWidth { return cut }
        }
        // Nothing fits: an opening word may still break at its FIRST op.
        if overflowFallback, prefix.isEmpty {
            for op in ops {          // ascending — the earliest usable op.
                if let cut = pieces(word, at: op, hyphenChar: hyphenChar) { return cut }
            }
        }
        return nil
    }

    /// Materialize the (head, tail) pair for one op, or nil when the
    /// offset is unusable — out of range, tail empty, or not a Character
    /// boundary (an offset inside a combining sequence is nonsense to
    /// split at, so it is SKIPPED rather than forced — same rule as the
    /// wave-40 dictionary walk, which CF answers in UTF-16 offsets).
    private static func pieces(_ word: Word, at op: Op,
                               hyphenChar: String) -> (head: String, tail: Word)? {
        let utf16 = word.text.utf16
        guard op.offset > 0,
              let u16 = utf16.index(utf16.startIndex, offsetBy: op.offset,
                                    limitedBy: utf16.endIndex),
              let idx = String.Index(u16, within: word.text) else { return nil }
        let suffix = String(word.text[idx...])
        guard !suffix.isEmpty, idx > word.text.startIndex else { return nil }
        // §5.3: a taken hyphenation point paints the hyphenate-character;
        // a literal-hyphen break-after point already ends in its glyph.
        let head = String(word.text[..<idx]) + (op.paintsHyphen ? hyphenChar : "")
        return (head, tail(word, splitAt: op.offset, suffix: suffix))
    }
}
