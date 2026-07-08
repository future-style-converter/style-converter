//
//  FontOpticalSizingExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//

import Foundation

enum FontOpticalSizingProperty { static let name = "FontOpticalSizing" }

enum FontOpticalSizingExtractor {
    static func extract(from properties: [IRProperty]) -> FontOpticalSizingConfig? {
        var cfg = FontOpticalSizingConfig()
        var touched = false
        for prop in properties where prop.type == FontOpticalSizingProperty.name {
            touched = true
            switch ValueExtractors.extractKeyword(prop.data)?.lowercased() {
            case "auto": cfg.mode = .auto
            case "none": cfg.mode = FontOpticalSizing.none
            default:     cfg.mode = nil
            }
        }
        return touched ? cfg : nil
    }
}
