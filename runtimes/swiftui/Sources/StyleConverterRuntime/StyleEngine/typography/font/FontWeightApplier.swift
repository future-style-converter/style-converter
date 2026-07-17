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
        // Lane IOS-TEXT fix 5 — the raw numeric weight rides along for
        // the css-fonts-4 §5.2 concrete-face pick in PlaceholderLabel.
        agg.fontWeightNumeric = cfg?.numeric
        agg.touched = true
    }
}
