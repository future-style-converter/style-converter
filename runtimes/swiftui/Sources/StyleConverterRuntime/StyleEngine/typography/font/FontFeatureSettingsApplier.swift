//
//  FontFeatureSettingsApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  TODO(phase-6+): route through UIFontDescriptor feature selectors when
//  SwiftUI exposes an attributed-string font-feature API. For now it's a
//  no-op; the data is preserved in the Config for future appliers.
//

import Foundation

enum FontFeatureSettingsApplier {
    // Identity contribution. Intentional — the Config data is captured,
    // but no SwiftUI modifier can consume it today.
    static func contribute(_ cfg: FontFeatureSettingsConfig?, into agg: inout TypographyAggregate) {
        _ = cfg; _ = agg
    }
}
