//
//  TextDecorationThicknessApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationThicknessApplier {
    static func contribute(_ cfg: TextDecorationThicknessConfig?, into agg: inout TypographyAggregate) {
        guard let px = cfg?.px else { return }
        agg.decorationThicknessPx = px   // TODO(phase-6+): CAShapeLayer underline
    }
}
