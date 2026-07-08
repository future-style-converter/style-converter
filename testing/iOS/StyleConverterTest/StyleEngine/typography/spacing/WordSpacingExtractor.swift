//
//  WordSpacingExtractor.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum WordSpacingProperty { static let name = "WordSpacing" }

enum WordSpacingExtractor {
    static func extract(from properties: [IRProperty]) -> WordSpacingConfig? {
        var cfg = WordSpacingConfig()
        var touched = false
        for prop in properties where prop.type == WordSpacingProperty.name {
            touched = true
            // `normal` keyword → no override.
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "normal" {
                cfg.px = nil; continue
            }
            cfg.px = ValueExtractors.extractPx(prop.data)
        }
        return touched ? cfg : nil
    }
}
