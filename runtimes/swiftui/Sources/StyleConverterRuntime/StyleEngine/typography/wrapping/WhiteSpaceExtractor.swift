//
//  WhiteSpaceExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum WhiteSpaceProperty { static let name = "WhiteSpace" }

enum WhiteSpaceExtractor {
    static func extract(from properties: [IRProperty]) -> WhiteSpaceConfig? {
        var cfg = WhiteSpaceConfig()
        var touched = false
        for prop in properties where prop.type == WhiteSpaceProperty.name {
            touched = true
            // The value is typically a keyword; for `hyphenate-character`
            // it's a plain string. extractKeyword handles both because it
            // falls through string → object forms identically.
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
