//
//  LineClampApplier.swift
//  StyleEngine/typography/line — Phase 6.
//

import Foundation

enum LineClampApplier {
    static func contribute(_ cfg: LineClampConfig?, into agg: inout TypographyAggregate) {
        guard let n = cfg?.lines else { return }
        // Keep the minimum of existing lineLimit (set by MaxLines) and
        // this one so the tightest clamp wins — matches CSS cascade.
        agg.lineLimit = min(agg.lineLimit ?? .max, n)
        agg.truncationMode = .tail
        agg.touched = true
    }
}
