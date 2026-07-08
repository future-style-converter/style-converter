//
//  LineClampExtractor.swift
//  StyleEngine/typography/line — Phase 6.
//

import Foundation

enum LineClampProperty { static let name = "LineClamp" }

enum LineClampExtractor {
    static func extract(from properties: [IRProperty]) -> LineClampConfig? {
        var cfg = LineClampConfig()
        var touched = false
        for prop in properties where prop.type == LineClampProperty.name {
            touched = true
            // Keyword `none` → clamp off.
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "none" {
                cfg.lines = nil
                continue
            }
            // Integer grammar. Clamp < 1 → nil (no clamp), matching CSS.
            if let n = ValueExtractors.extractInt(prop.data), n >= 1 {
                cfg.lines = n
            }
        }
        return touched ? cfg : nil
    }
}
