//
//  WhiteSpaceApplier.swift
//  StyleEngine/typography/wrapping — Phase 6.
//
//  Fidelity wave 2: `white-space: nowrap | pre` suppresses soft wrap
//  (css-text-4 §5.1 — the legacy shorthand expands to text-wrap-mode:
//  nowrap for both keywords). Other keywords (pre-wrap, pre-line,
//  break-spaces) keep wrapping — identity here. Whitespace COLLAPSING
//  has no SwiftUI analogue; placeholder text carries no space runs so
//  only the wrap bit is observable.
//

import Foundation

enum WhiteSpaceApplier {
    static func contribute(_ cfg: WhiteSpaceConfig?, into agg: inout TypographyAggregate) {
        // Keyword arrives serializer-uppercased ("NOWRAP"); the extractor
        // lowercases before storing.
        switch cfg?.keyword {
        case "nowrap", "pre":
            // Both keywords disable soft wrapping per the css-text-4
            // shorthand expansion table (text-wrap-mode: nowrap).
            agg.noWrap = true
            agg.touched = true
            // `pre` ALSO preserves space runs (css-text-3 §4.1.2) —
            // flagged for completeness even though the noWrap gate above
            // already keeps the greedy pre-break away from this text.
            if cfg?.keyword == "pre" { agg.preservesSpaces = true }
        case "pre-wrap", "pre_wrap", "break-spaces", "break_spaces":
            // Lane IOS wave 5 — css-text-3 §4.1.2: these keywords keep
            // wrapping ON but PRESERVE white-space runs. The greedy
            // pre-break (PlaceholderLabel) splits on spaces and re-joins
            // with single separators, which would COLLAPSE preserved
            // runs — a glyph-content rewrite. The flag gates the
            // pre-break off so preserved text takes the legacy soft-wrap
            // path. Both spellings accepted: the live wire ships the
            // serializer's enum name ("PRE_WRAP", lowercased by the
            // extractor to "pre_wrap" — WhiteSpaceProperty.kt), the
            // hyphen form covers CSS-keyword-shaped documents.
            agg.preservesSpaces = true
            agg.touched = true
        default:
            // normal / pre-line → collapse + wrap: pre-line collapses
            // space RUNS like `normal` (only segment breaks survive,
            // css-text-3 §4.1.1 row 3), so the greedy pre-break's
            // space-collapse split stays faithful there — identity here.
            break
        }
    }
}
