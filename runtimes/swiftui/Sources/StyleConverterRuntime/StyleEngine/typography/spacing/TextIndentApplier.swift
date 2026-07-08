//
//  TextIndentApplier.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum TextIndentApplier {
    static func contribute(_ cfg: TextIndentConfig?, into agg: inout TypographyAggregate) {
        guard let px = cfg?.px else { return }
        // Captured for audit. TODO(phase-6+): apply via first-line inset.
        agg.textIndentPx = px
    }
}
