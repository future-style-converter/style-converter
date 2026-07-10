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
        default:
            // normal / pre-wrap / pre-line / break-spaces → wrap allowed.
            break
        }
    }
}
