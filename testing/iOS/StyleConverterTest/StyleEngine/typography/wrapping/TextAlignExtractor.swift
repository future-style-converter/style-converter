//
//  TextAlignExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import SwiftUI

enum TextAlignProperty { static let name = "TextAlign" }

enum TextAlignExtractor {
    static func extract(from properties: [IRProperty]) -> TextAlignConfig? {
        var cfg = TextAlignConfig()
        var touched = false
        for prop in properties where prop.type == TextAlignProperty.name {
            touched = true
            switch ValueExtractors.normalize(ValueExtractors.extractKeyword(prop.data)) {
            case "CENTER":        cfg.alignment = .center
            case "RIGHT", "END":  cfg.alignment = .trailing
            case "LEFT", "START": cfg.alignment = .leading
            // `justify` / `justify-all` / `match-parent` → fall back to
            // leading; TODO via NSAttributedString.
            default:              cfg.alignment = .leading
            }
        }
        return touched ? cfg : nil
    }
}
