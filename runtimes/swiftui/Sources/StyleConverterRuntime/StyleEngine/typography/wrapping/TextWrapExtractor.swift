//
//  TextWrapExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum TextWrapProperty { static let name = "TextWrap" }

enum TextWrapExtractor {
    static func extract(from properties: [IRProperty]) -> TextWrapConfig? {
        var cfg = TextWrapConfig()
        var touched = false
        for prop in properties where prop.type == TextWrapProperty.name {
            touched = true
            // The value is typically a keyword; for `hyphenate-character`
            // it's a plain string. extractKeyword handles both because it
            // falls through string → object forms identically.
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
