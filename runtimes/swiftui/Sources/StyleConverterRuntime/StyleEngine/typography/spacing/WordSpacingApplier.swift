//
//  WordSpacingApplier.swift
//  StyleEngine/typography/spacing — Phase 6.
//
//  Lane IOS-TEXT fix 2: `word-spacing` now RENDERS. The aggregate value
//  bridges through TextConfig into PlaceholderLabel, which applies it as
//  an AttributedString `.kern` on each space character (css-text-3 §8.1:
//  word-spacing adds to the advance of each word-separator character) —
//  the pre-fix applier stored the px and nothing ever consumed it.
//

// Foundation for AttributedString; the kern attribute scope comes from
// SwiftUI (AttributeScopes.SwiftUIAttributes.KernAttribute).
import SwiftUI

enum WordSpacingApplier {
    static func contribute(_ cfg: WordSpacingConfig?, into agg: inout TypographyAggregate) {
        if let px = cfg?.px {
            agg.wordSpacingPx = px
            // touched now flips: the value has a real render path.
            agg.touched = true
        }
        // Lane IOS-TEXT fix 3 — em/rem word-spacing parks here and
        // resolves in TypographyExtractor (same lane as letter-spacing).
        if let rel = cfg?.relative {
            agg.wordSpacingRelative = rel
            agg.touched = true
        }
    }

    /// Lane IOS wave 5 (findings 2 + 6) — the RENDER twin of
    /// `GreedyLineBreaker.measurer`'s attribute model, so fit tests and
    /// on-screen glyphs share ONE spacing model:
    ///   • word-spacing rides `.kern` on each word separator — U+0020
    ///     SPACE and U+00A0 NO-BREAK SPACE (css-text-3 §8.1 lists both;
    ///     Compose's applyWordSpacingSpans handles both).
    ///   • when letter-spacing is ALSO declared it CANNOT ride the
    ///     box-level `.tracking()` (SwiftUI documents tracking as
    ///     overriding kerning on the same Text — it silently suppressed
    ///     the space kerns, making word-spacing a no-op), so it is baked
    ///     as `.kern` on EVERY character here; separators get the SUM
    ///     (css-text-3 §8.1/§8.2: both add to the advance).
    /// Returns nil when no kern attribute is needed at all — the caller
    /// renders a plain `Text(String)`, keeping every existing render
    /// byte-stable (letter-spacing alone stays on the legacy tracking).
    /// Pure — per-character kerns pinned by IOSTextLaneTests.
    static func kernedRun(text: String,
                          letterSpacingPx: CGFloat?,
                          wordSpacingPx: CGFloat?) -> AttributedString? {
        // No word-spacing → nothing this builder owns (tracking lane).
        guard let ws = wordSpacingPx else { return nil }
        // Word-spacing without letter-spacing and a zero value is the
        // identity — skip the attributed rebuild entirely.
        if ws == 0 && letterSpacingPx == nil { return nil }
        // Uniform base kern: letter-spacing on every glyph when declared
        // (the tracking substitute), else no base.
        let base = letterSpacingPx ?? 0
        var attr = AttributedString(text)
        var idx = attr.startIndex
        while idx < attr.endIndex {
            let next = attr.index(afterCharacter: idx)
            let ch = attr.characters[idx]
            // Separator advance = base + word-spacing (both specs add).
            let isSeparator = ch == " " || ch == "\u{00A0}"
            let kern = isSeparator ? base + ws : base
            // Only write non-zero kerns so a plain glyph in the
            // ws-only lane keeps its natural advance attribute-free.
            if kern != 0 { attr[idx..<next].kern = kern }
            idx = next
        }
        return attr
    }
}
