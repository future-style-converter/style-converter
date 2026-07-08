//
//  TextOverflowExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum TextOverflowProperty { static let name = "TextOverflow" }

enum TextOverflowExtractor {
    static func extract(from properties: [IRProperty]) -> TextOverflowConfig? {
        var cfg = TextOverflowConfig()
        var touched = false
        for prop in properties where prop.type == TextOverflowProperty.name {
            touched = true
            // Keyword forms first.
            if let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased() {
                switch kw {
                case "clip":     cfg.mode = .clip
                case "ellipsis": cfg.mode = .ellipsis
                case "fade":     cfg.mode = .fade
                default:         cfg.mode = .customString(kw)
                }
                continue
            }
            // Plain string for custom truncation glyph.
            if let s = prop.data.stringValue { cfg.mode = .customString(s) }
        }
        return touched ? cfg : nil
    }
}
