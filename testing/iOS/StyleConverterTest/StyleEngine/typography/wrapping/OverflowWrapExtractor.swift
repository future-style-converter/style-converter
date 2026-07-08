//
//  OverflowWrapExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum OverflowWrapProperty { static let name = "OverflowWrap" }

enum OverflowWrapExtractor {
    static func extract(from properties: [IRProperty]) -> OverflowWrapConfig? {
        var cfg = OverflowWrapConfig()
        var touched = false
        for prop in properties where prop.type == OverflowWrapProperty.name {
            touched = true
            // The value is typically a keyword; for `hyphenate-character`
            // it's a plain string. extractKeyword handles both because it
            // falls through string → object forms identically.
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
