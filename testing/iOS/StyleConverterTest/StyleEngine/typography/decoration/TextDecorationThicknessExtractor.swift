//
//  TextDecorationThicknessExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationThicknessProperty { static let name = "TextDecorationThickness" }

enum TextDecorationThicknessExtractor {
    static func extract(from properties: [IRProperty]) -> TextDecorationThicknessConfig? {
        var cfg = TextDecorationThicknessConfig()
        var touched = false
        for prop in properties where prop.type == TextDecorationThicknessProperty.name {
            touched = true
            // `auto` / `from-font` keywords → nil (platform default).
            if ValueExtractors.extractKeyword(prop.data) != nil { cfg.px = nil; continue }
            cfg.px = ValueExtractors.extractPx(prop.data)
        }
        return touched ? cfg : nil
    }
}
