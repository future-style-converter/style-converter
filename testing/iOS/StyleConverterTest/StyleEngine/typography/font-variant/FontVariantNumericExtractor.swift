//
//  FontVariantNumericExtractor.swift
//  StyleEngine/typography/font-variant — Phase 6.
//
//  Keyword-list extractor for CSS `font-variant-numeric`. Shares the
//  FontVariantKeywordListConfig shape with its sibling font-variant-*
//  properties (see FontVariantKeywordListConfig.swift for rationale).
//

import Foundation

enum FontVariantNumericProperty { static let name = "FontVariantNumeric" }

enum FontVariantNumericExtractor {
    /// Last-wins scan. Returns nil when no `FontVariantNumeric` property was seen.
    static func extract(from properties: [IRProperty]) -> FontVariantKeywordListConfig? {
        var cfg = FontVariantKeywordListConfig()
        var touched = false
        for prop in properties where prop.type == FontVariantNumericProperty.name {
            touched = true
            // Delegate to the shared keyword-list parser so the six
            // near-identical properties stay byte-consistent.
            cfg.keywords = FontVariantKeywordParse.parse(prop.data)
        }
        return touched ? cfg : nil
    }
}
