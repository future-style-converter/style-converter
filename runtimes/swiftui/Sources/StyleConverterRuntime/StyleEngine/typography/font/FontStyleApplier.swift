//
//  FontStyleApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  Italic folds into the aggregate and is applied via `.italic()` in
//  TypographyApplier.
//

import Foundation

enum FontStyleApplier {
    static func contribute(_ cfg: FontStyleConfig?, into agg: inout TypographyAggregate) {
        guard let italic = cfg?.italic else { return }
        agg.italic = italic
        agg.touched = true
    }
}
