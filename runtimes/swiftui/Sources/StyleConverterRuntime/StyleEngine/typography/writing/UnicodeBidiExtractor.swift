//
//  UnicodeBidiExtractor.swift
//  StyleEngine/typography/writing — Phase 6.
//

import Foundation

enum UnicodeBidiProperty { static let name = "UnicodeBidi" }

enum UnicodeBidiExtractor {
    static func extract(from properties: [IRProperty]) -> UnicodeBidiConfig? {
        var cfg = UnicodeBidiConfig()
        var touched = false
        for prop in properties where prop.type == UnicodeBidiProperty.name {
            touched = true
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
