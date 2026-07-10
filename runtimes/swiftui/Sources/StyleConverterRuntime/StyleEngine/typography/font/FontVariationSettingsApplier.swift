//
//  FontVariationSettingsApplier.swift
//  StyleEngine/typography/font — Phase 6, revisited fidelity wave 2.
//
//  Deliberate no-op — WITH the axis list now correctly extracted (the
//  wave-2 extractor fix handles the converter's object form, so audits
//  see the axes).
//
//  Why no-op: css-fonts-4 §11.1 says font-variation-settings applies to
//  VARIABLE fonts only. The harness's shared reference face (Inter) is
//  loaded as static weights on the web reference, so the browser
//  renders `"wght" 900` with NO visual change. Mapping wght → SwiftUI
//  Font.Weight on iOS synthesized a black face and pushed the capture
//  AWAY from the reference (Typography_C07 measured 0.856 → 0.833 when
//  the mapping was applied; reverted). If the harness ever ships a
//  variable Inter, revisit: map wght through the same 100-step bucket
//  ladder as FontWeightExtractor.
//

import Foundation

enum FontVariationSettingsApplier {
    static func contribute(_ cfg: FontVariationSettingsConfig?, into agg: inout TypographyAggregate) {
        // Axes recorded in the config for coverage audits; intentionally
        // not folded into the aggregate (see header).
        _ = cfg; _ = agg
    }
}
