//
//  TextJustifyExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum TextJustifyProperty { static let name = "TextJustify" }

enum TextJustifyExtractor {
    static func extract(from properties: [IRProperty]) -> TextJustifyConfig? {
        var cfg = TextJustifyConfig()
        var touched = false
        for prop in properties where prop.type == TextJustifyProperty.name {
            touched = true
            // The value is typically a keyword; for `hyphenate-character`
            // it's a plain string. extractKeyword handles both because it
            // falls through string → object forms identically.
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
