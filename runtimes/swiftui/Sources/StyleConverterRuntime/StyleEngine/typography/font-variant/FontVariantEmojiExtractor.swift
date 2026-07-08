//
//  FontVariantEmojiExtractor.swift
//  StyleEngine/typography/font-variant — Phase 6.
//
//  Keyword-list extractor for CSS `font-variant-emoji`. Shares the
//  FontVariantKeywordListConfig shape with its sibling font-variant-*
//  properties (see FontVariantKeywordListConfig.swift for rationale).
//

import Foundation

enum FontVariantEmojiProperty { static let name = "FontVariantEmoji" }

enum FontVariantEmojiExtractor {
    /// Last-wins scan. Returns nil when no `FontVariantEmoji` property was seen.
    static func extract(from properties: [IRProperty]) -> FontVariantKeywordListConfig? {
        var cfg = FontVariantKeywordListConfig()
        var touched = false
        for prop in properties where prop.type == FontVariantEmojiProperty.name {
            touched = true
            // Delegate to the shared keyword-list parser so the six
            // near-identical properties stay byte-consistent.
            cfg.keywords = FontVariantKeywordParse.parse(prop.data)
        }
        return touched ? cfg : nil
    }
}
