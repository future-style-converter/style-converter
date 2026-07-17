//
//  TextTransformApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

// SwiftUI for Text.Case in the render-string rewrite (fix below).
import SwiftUI

enum TextTransformApplier {
    static func contribute(_ cfg: TextTransformConfig?, into agg: inout TypographyAggregate) {
        guard let tc = cfg?.textCase else { return }
        agg.textCase = tc
        // Lane IOS-TEXT fix 6 — carry the capitalize flag to the
        // aggregate so StyleBuilder can bridge it into TextConfig for
        // PlaceholderLabel's string-level rewrite (SwiftUI has no
        // `.textCase` member for CSS `capitalize`).
        agg.capitalizeWords = cfg?.capitalize ?? false
        agg.touched = true
    }

    /// css-text-3 §2.1 `capitalize`: titlecase the first TYPOGRAPHIC
    /// LETTER UNIT of each word; other characters are untouched. Word
    /// boundary = the space separator, mirroring Compose's capitalize
    /// lane so the two native runtimes transform identically.
    ///
    /// Lane IOS wave 5 (finding 5, WPT capitalize-031): leading
    /// punctuation is NOT the letter unit — "(hello" must render
    /// "(Hello", but the old word[0]-if-letter rewrite uppercased the
    /// "(" (a no-op) and left the "h" lowercase. The target is now the
    /// first letter-or-digit: punctuation/symbols are skipped, and a
    /// DIGIT ends the search without a rewrite ("123abc" stays
    /// "123abc" — Blink's word segmenter treats the digit as the word's
    /// first unit and titlecasing a digit is the identity, matching the
    /// Chromium reference). Pure — pinned by IOSTextLaneTests.
    static func capitalizeWords(_ s: String) -> String {
        // Keep empty fragments so runs of spaces round-trip verbatim
        // (the transform must never change glyph advances/word count).
        s.split(separator: " ", omittingEmptySubsequences: false)
            .map { word -> String in
                // First letter-or-digit = the typographic target unit
                // (digits included so "123abc" matches Blink: the digit
                // is the unit, uppercasing it changes nothing).
                guard let idx = word.firstIndex(where: { $0.isLetter || $0.isNumber })
                else { return String(word) }   // all-punctuation word → verbatim
                // Titlecase ONLY that unit; the leading punctuation and
                // the tail round-trip byte-for-byte. `uppercased()`
                // matches Kotlin's `titlecase` for the Latin range the
                // fixtures exercise (titlecase digraphs out of scope).
                return String(word[word.startIndex..<idx])
                    + String(word[idx]).uppercased()
                    + String(word[word.index(after: idx)...])
            }
            .joined(separator: " ")
    }

    /// Lane IOS wave 5 (finding 1) — the RENDER-string rewrite for
    /// text-transform, applied BEFORE the greedy pre-break measures the
    /// run (css-text-3 §2.1: the transform changes glyph advances, so
    /// measuring the untransformed string commits line breaks that
    /// overflow once `.textCase` uppercases them at render time and
    /// TextKit re-breaks — push-out included). PlaceholderLabel calls
    /// this and then SUPPRESSES the box-level `.textCase` environment
    /// for its own Text (`.textCase(nil)`, innermost wins) so measure
    /// and render share exactly one string — the same model as
    /// Compose's placeholderDisplayText. The three CSS keywords are
    /// mutually exclusive (one `text-transform` value per element), so
    /// case fold and capitalize never compose. Pure — XCTest-pinned.
    static func renderString(_ s: String,
                             textCase: Text.Case?,
                             capitalize: Bool) -> String {
        switch textCase {
        // `uppercase` / `lowercase` — full-string case fold, exactly
        // what `.textCase(.uppercase/.lowercase)` would have rendered.
        case .uppercase: return s.uppercased()
        case .lowercase: return s.lowercased()
        // No case transform → `capitalize` (string-level, SwiftUI has
        // no Text.Case member for it) or identity.
        default: return capitalize ? capitalizeWords(s) : s
        }
    }
}
