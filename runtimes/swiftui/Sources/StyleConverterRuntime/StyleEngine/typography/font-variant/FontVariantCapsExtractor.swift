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
            // Fidelity wave 2: the converter serializes enum keywords in
            // SCREAMING_SNAKE ("SMALL_CAPS"), so normalise underscores to
            // hyphens after lowercasing — otherwise small-caps never
            // matched and Typography_C06 row 005 lost its caps variant.
            switch ValueExtractors.extractKeyword(prop.data)?.lowercased()
                .replacingOccurrences(of: "_", with: "-") {
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
