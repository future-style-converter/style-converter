//
//  WordSpacingApplier.swift
//  StyleEngine/typography/spacing — Phase 6.
//
//  TODO(phase-6+): route via NSAttributedString when SwiftUI gains a
//  word-level tracking API. Today the value is captured for audit only.
//

import Foundation

enum WordSpacingApplier {
    static func contribute(_ cfg: WordSpacingConfig?, into agg: inout TypographyAggregate) {
        guard let px = cfg?.px else { return }
        agg.wordSpacingPx = px   // captured, not rendered
    }
}
