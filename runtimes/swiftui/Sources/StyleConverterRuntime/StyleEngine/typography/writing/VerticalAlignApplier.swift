//
//  VerticalAlignApplier.swift
//  StyleEngine/typography/writing — Phase 6.
//

import Foundation

enum VerticalAlignApplier {
    static func contribute(_ cfg: VerticalAlignConfig?, into agg: inout TypographyAggregate) {
        guard let px = cfg?.offsetPx else { return }
        agg.baselineOffsetPx = px
        agg.touched = true
    }
}
