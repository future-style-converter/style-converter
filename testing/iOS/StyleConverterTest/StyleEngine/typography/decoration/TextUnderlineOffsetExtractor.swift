//
//  TextUnderlineOffsetExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextUnderlineOffsetProperty { static let name = "TextUnderlineOffset" }

enum TextUnderlineOffsetExtractor {
    static func extract(from properties: [IRProperty]) -> TextUnderlineOffsetConfig? {
        var cfg = TextUnderlineOffsetConfig()
        var touched = false
        for prop in properties where prop.type == TextUnderlineOffsetProperty.name {
            touched = true
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "auto" {
                cfg.px = nil; continue
            }
            cfg.px = ValueExtractors.extractPx(prop.data)
        }
        return touched ? cfg : nil
    }
}
