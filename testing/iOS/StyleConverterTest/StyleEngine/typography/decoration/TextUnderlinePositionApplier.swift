//
//  TextUnderlinePositionApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextUnderlinePositionApplier {
    // Identity contribution. TODO(phase-6+): route via NSAttributedString.
    static func contribute(_ cfg: TextUnderlinePositionConfig?, into agg: inout TypographyAggregate) {
        _ = cfg; _ = agg
    }
}
