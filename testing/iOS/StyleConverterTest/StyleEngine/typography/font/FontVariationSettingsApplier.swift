//
//  FontVariationSettingsApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  TODO(phase-6+): plumb through UIFontDescriptor variation attributes
//  when SwiftUI exposes a suitable Text-level API.
//

import Foundation

enum FontVariationSettingsApplier {
    static func contribute(_ cfg: FontVariationSettingsConfig?, into agg: inout TypographyAggregate) {
        _ = cfg; _ = agg   // no-op by design
    }
}
