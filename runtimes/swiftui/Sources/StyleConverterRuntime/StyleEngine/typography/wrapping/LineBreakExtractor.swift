//
//  LineBreakExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum LineBreakProperty { static let name = "LineBreak" }

enum LineBreakExtractor {
    static func extract(from properties: [IRProperty]) -> LineBreakConfig? {
        var cfg = LineBreakConfig()
        var touched = false
        for prop in properties where prop.type == LineBreakProperty.name {
            touched = true
            // The value is typically a keyword; for `hyphenate-character`
            // it's a plain string. extractKeyword handles both because it
            // falls through string → object forms identically.
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
