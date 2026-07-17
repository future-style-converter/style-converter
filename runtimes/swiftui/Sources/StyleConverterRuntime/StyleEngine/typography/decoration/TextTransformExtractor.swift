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
            case "uppercase":  cfg.textCase = .some(.uppercase); cfg.capitalize = false
            case "lowercase":  cfg.textCase = .some(.lowercase); cfg.capitalize = false
            case "none":       cfg.textCase = .some(nil); cfg.capitalize = false  // explicit "no transform"
            // Lane IOS-TEXT fix 6: `capitalize` has no Text.Case member —
            // flag it for the string-level word-titlecase rewrite in
            // PlaceholderLabel (css-text-3 §2.1). textCase stays
            // `.some(nil)` so any inherited environment case transform is
            // explicitly cleared (last declaration wins, as in CSS).
            case "capitalize": cfg.textCase = .some(nil); cfg.capitalize = true
            default:           cfg.textCase = nil; cfg.capitalize = false        // unknown → inherit
            }
        }
        return touched ? cfg : nil
    }
}
