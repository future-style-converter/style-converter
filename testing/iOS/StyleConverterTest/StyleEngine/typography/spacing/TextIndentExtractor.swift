//
//  TextIndentExtractor.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum TextIndentProperty { static let name = "TextIndent" }

enum TextIndentExtractor {
    static func extract(from properties: [IRProperty]) -> TextIndentConfig? {
        var cfg = TextIndentConfig()
        var touched = false
        for prop in properties where prop.type == TextIndentProperty.name {
            touched = true
            // `hanging` / `each-line` keyword flags are dropped on iOS.
            cfg.px = ValueExtractors.extractPx(prop.data)
        }
        return touched ? cfg : nil
    }
}
