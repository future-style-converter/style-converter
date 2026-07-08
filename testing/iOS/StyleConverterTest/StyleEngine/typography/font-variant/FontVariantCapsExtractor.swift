//
//  FontVariantCapsExtractor.swift
//  StyleEngine/typography/font-variant — Phase 6.
//

import Foundation

enum FontVariantCapsProperty { static let name = "FontVariantCaps" }

enum FontVariantCapsExtractor {
    static func extract(from properties: [IRProperty]) -> FontVariantCapsConfig? {
        var cfg = FontVariantCapsConfig()
        var touched = false
        for prop in properties where prop.type == FontVariantCapsProperty.name {
            touched = true
            // All grammar branches are keywords — extractKeyword covers both
            // plain strings and { keyword: "…" } object forms.
            switch ValueExtractors.extractKeyword(prop.data)?.lowercased() {
            case "normal":          cfg.mode = .normal
            case "small-caps":      cfg.mode = .smallCaps
            case "all-small-caps":  cfg.mode = .allSmallCaps
            case "petite-caps":     cfg.mode = .petiteCaps
            case "all-petite-caps": cfg.mode = .allPetiteCaps
            case "unicase":         cfg.mode = .unicase
            case "titling-caps":    cfg.mode = .titlingCaps
            default:                cfg.mode = nil
            }
        }
        return touched ? cfg : nil
    }
}
