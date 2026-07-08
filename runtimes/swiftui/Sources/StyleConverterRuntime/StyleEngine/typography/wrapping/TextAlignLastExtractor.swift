//
//  TextAlignLastExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum TextAlignLastProperty { static let name = "TextAlignLast" }

enum TextAlignLastExtractor {
    static func extract(from properties: [IRProperty]) -> TextAlignLastConfig? {
        var cfg = TextAlignLastConfig()
        var touched = false
        for prop in properties where prop.type == TextAlignLastProperty.name {
            touched = true
            // The value is typically a keyword; for `hyphenate-character`
            // it's a plain string. extractKeyword handles both because it
            // falls through string → object forms identically.
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
