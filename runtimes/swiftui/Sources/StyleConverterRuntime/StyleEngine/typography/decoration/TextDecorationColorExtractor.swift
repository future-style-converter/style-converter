//
//  TextDecorationColorExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationColorProperty { static let name = "TextDecorationColor" }

enum TextDecorationColorExtractor {
    static func extract(from properties: [IRProperty]) -> TextDecorationColorConfig? {
        var cfg = TextDecorationColorConfig()
        var touched = false
        for prop in properties where prop.type == TextDecorationColorProperty.name {
            touched = true
            cfg.color = ValueExtractors.extractColor(prop.data)
        }
        return touched ? cfg : nil
    }
}
