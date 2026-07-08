//
//  HyphenateCharacterExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum HyphenateCharacterProperty { static let name = "HyphenateCharacter" }

enum HyphenateCharacterExtractor {
    static func extract(from properties: [IRProperty]) -> HyphenateCharacterConfig? {
        var cfg = HyphenateCharacterConfig()
        var touched = false
        for prop in properties where prop.type == HyphenateCharacterProperty.name {
            touched = true
            // The value is typically a keyword; for `hyphenate-character`
            // it's a plain string. extractKeyword handles both because it
            // falls through string → object forms identically.
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
