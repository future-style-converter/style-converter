//
//  WordBreakExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum WordBreakProperty { static let name = "WordBreak" }

enum WordBreakExtractor {
    static func extract(from properties: [IRProperty]) -> WordBreakConfig? {
        var cfg = WordBreakConfig()
        var touched = false
        for prop in properties where prop.type == WordBreakProperty.name {
            touched = true
            // The value is typically a keyword; for `hyphenate-character`
            // it's a plain string. extractKeyword handles both because it
            // falls through string → object forms identically.
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
