//
//  LetterSpacingExtractor.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum LetterSpacingProperty { static let name = "LetterSpacing" }

enum LetterSpacingExtractor {
    static func extract(from properties: [IRProperty]) -> LetterSpacingConfig? {
        var cfg = LetterSpacingConfig()
        var touched = false
        for prop in properties where prop.type == LetterSpacingProperty.name {
            touched = true
            // `normal` keyword → no override. Lengths go through extractPx.
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "normal" {
                cfg.px = nil; continue
            }
            cfg.px = ValueExtractors.extractPx(prop.data)
        }
        return touched ? cfg : nil
    }
}
