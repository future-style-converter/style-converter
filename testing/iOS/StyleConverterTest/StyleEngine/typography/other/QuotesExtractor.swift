//
//  QuotesExtractor.swift
//  StyleEngine/typography/other — Phase 6.
//

import Foundation

enum QuotesProperty { static let name = "Quotes" }

enum QuotesExtractor {
    static func extract(from properties: [IRProperty]) -> QuotesConfig? {
        var cfg = QuotesConfig()
        var touched = false
        for prop in properties where prop.type == QuotesProperty.name {
            touched = true
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
