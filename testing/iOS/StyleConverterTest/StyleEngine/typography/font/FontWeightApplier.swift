//
//  FontWeightApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  Folds the weight into the shared aggregate. TypographyApplier then
//  chains `.fontWeight(_:)` on the Text view.
//

import Foundation

enum FontWeightApplier {
    static func contribute(_ cfg: FontWeightConfig?, into agg: inout TypographyAggregate) {
        // Guard against accidental nil overwrite from a later empty pass.
        guard let w = cfg?.weight else { return }
        agg.fontWeight = w
        agg.touched = true
    }
}
