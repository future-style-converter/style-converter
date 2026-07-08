//
//  FontVariantNumericApplier.swift
//  StyleEngine/typography/font-variant — Phase 6.
//
//  SwiftUI Text has no direct API for `font-variant-numeric` — applier is
//  identity. Keyword data is still captured in the Config so a future
//  UIFontDescriptor-feature applier can route it without re-parsing the IR.
//

import Foundation

enum FontVariantNumericApplier {
    static func contribute(_ cfg: FontVariantKeywordListConfig?, into agg: inout TypographyAggregate) {
        _ = cfg; _ = agg   // no-op; TODO(phase-6+): UIFontDescriptor feature routing.
    }
}
