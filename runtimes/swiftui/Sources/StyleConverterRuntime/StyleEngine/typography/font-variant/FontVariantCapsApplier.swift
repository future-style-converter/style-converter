//
//  FontVariantCapsApplier.swift
//  StyleEngine/typography/font-variant — Phase 6.
//
//  Routes small-caps variants to the aggregate's `smallCaps` flag so
//  TypographyApplier can call `.smallCaps()`. Other modes TODO.
//

import Foundation

enum FontVariantCapsApplier {
    static func contribute(_ cfg: FontVariantCapsConfig?, into agg: inout TypographyAggregate) {
        switch cfg?.mode {
        case .smallCaps, .allSmallCaps, .petiteCaps, .allPetiteCaps:
            // SwiftUI Text API doesn't distinguish petite vs. small; both
            // bucket to `.smallCaps()`. Matches the legacy iOS renderer.
            agg.smallCaps = true
            agg.touched = true
        default:
            // `normal`, `unicase`, `titling-caps`, nil → identity.
            // TODO(phase-6+): titling/unicase via UIFontDescriptor features.
            break
        }
    }
}
