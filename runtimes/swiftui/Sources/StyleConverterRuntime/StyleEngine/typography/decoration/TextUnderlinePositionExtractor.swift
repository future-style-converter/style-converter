//
//  TextUnderlinePositionExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextUnderlinePositionProperty { static let name = "TextUnderlinePosition" }

enum TextUnderlinePositionExtractor {
    static func extract(from properties: [IRProperty]) -> TextUnderlinePositionConfig? {
        var cfg = TextUnderlinePositionConfig()
        var touched = false
        for prop in properties where prop.type == TextUnderlinePositionProperty.name {
            touched = true
            cfg.keyword = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
