//
//  TextTransformExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import SwiftUI

enum TextTransformProperty { static let name = "TextTransform" }

enum TextTransformExtractor {
    static func extract(from properties: [IRProperty]) -> TextTransformConfig? {
        var cfg = TextTransformConfig()
        var touched = false
        for prop in properties where prop.type == TextTransformProperty.name {
            touched = true
            switch ValueExtractors.extractKeyword(prop.data)?.lowercased() {
            case "uppercase":  cfg.textCase = .some(.uppercase)
            case "lowercase":  cfg.textCase = .some(.lowercase)
            case "none":       cfg.textCase = .some(nil)       // explicit "no transform"
            case "capitalize": cfg.textCase = .some(nil)       // TODO: titlecase via custom applier
            default:           cfg.textCase = nil              // unknown → inherit
            }
        }
        return touched ? cfg : nil
    }
}
