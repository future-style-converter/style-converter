//
//  FontKerningExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//

import Foundation

enum FontKerningProperty { static let name = "FontKerning" }

enum FontKerningExtractor {
    static func extract(from properties: [IRProperty]) -> FontKerningConfig? {
        var cfg = FontKerningConfig()
        var touched = false
        for prop in properties where prop.type == FontKerningProperty.name {
            touched = true
            // Keyword-only grammar. The parser emits a plain string or
            // { keyword: "..." } object — extractKeyword handles both.
            switch ValueExtractors.extractKeyword(prop.data)?.lowercased() {
            case "auto":   cfg.mode = .auto
            case "normal": cfg.mode = .normal
            case "none":   cfg.mode = FontKerningMode.none
            default:       cfg.mode = nil
            }
        }
        return touched ? cfg : nil
    }
}
