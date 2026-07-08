//
//  DirectionApplier.swift
//  StyleEngine/typography/writing — Phase 6.
//

import Foundation

enum DirectionApplier {
    static func contribute(_ cfg: DirectionConfig?, into agg: inout TypographyAggregate) {
        guard let d = cfg?.direction else { return }
        agg.layoutDirection = d
        agg.touched = true
    }
}
