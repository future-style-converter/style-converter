//
//  TextRenderingExtractor.swift
//  StyleEngine/typography/other — Phase 6.
//

import Foundation

enum TextRenderingProperty { static let name = "TextRendering" }

enum TextRenderingExtractor {
    static func extract(from properties: [IRProperty]) -> TextRenderingConfig? {
        var cfg = TextRenderingConfig()
        var touched = false
        for prop in properties where prop.type == TextRenderingProperty.name {
            touched = true
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
