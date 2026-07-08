//
//  MaxLinesApplier.swift
//  StyleEngine/typography/line — Phase 6.
//

import Foundation

enum MaxLinesApplier {
    static func contribute(_ cfg: MaxLinesConfig?, into agg: inout TypographyAggregate) {
        guard let n = cfg?.lines else { return }
        // Tightest clamp wins when both MaxLines and LineClamp are present.
        agg.lineLimit = min(agg.lineLimit ?? .max, n)
        agg.touched = true
    }
}
