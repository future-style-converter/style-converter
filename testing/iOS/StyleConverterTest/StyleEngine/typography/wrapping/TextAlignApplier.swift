//
//  TextAlignApplier.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum TextAlignApplier {
    static func contribute(_ cfg: TextAlignConfig?, into agg: inout TypographyAggregate) {
        guard let a = cfg?.alignment else { return }
        agg.textAlign = a
        agg.touched = true
    }
}
