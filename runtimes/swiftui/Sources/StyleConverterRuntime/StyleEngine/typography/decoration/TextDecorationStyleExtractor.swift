//
//  TextDecorationStyleExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationStyleProperty { static let name = "TextDecorationStyle" }

enum TextDecorationStyleExtractor {
    static func extract(from properties: [IRProperty]) -> TextDecorationStyleConfig? {
        var cfg = TextDecorationStyleConfig()
        var touched = false
        for prop in properties where prop.type == TextDecorationStyleProperty.name {
            touched = true
            switch ValueExtractors.extractKeyword(prop.data)?.lowercased() {
            case "solid":  cfg.pattern = .solid
            case "double": cfg.pattern = .double
            case "dotted": cfg.pattern = .dotted
            case "dashed": cfg.pattern = .dashed
            case "wavy":   cfg.pattern = .wavy
            default:       cfg.pattern = .solid
            }
        }
        return touched ? cfg : nil
    }
}
