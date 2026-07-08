//
//  TextDecorationColorApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationColorApplier {
    static func contribute(_ cfg: TextDecorationColorConfig?, into agg: inout TypographyAggregate) {
        guard let c = cfg?.color else { return }
        agg.decorationColor = c
        agg.touched = true
    }
}
