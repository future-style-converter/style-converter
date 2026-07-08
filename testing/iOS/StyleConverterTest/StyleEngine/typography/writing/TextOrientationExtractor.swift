//
//  TextOrientationExtractor.swift
//  StyleEngine/typography/writing — Phase 6.
//

import Foundation

enum TextOrientationProperty { static let name = "TextOrientation" }

enum TextOrientationExtractor {
    static func extract(from properties: [IRProperty]) -> TextOrientationConfig? {
        var cfg = TextOrientationConfig()
        var touched = false
        for prop in properties where prop.type == TextOrientationProperty.name {
            touched = true
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
