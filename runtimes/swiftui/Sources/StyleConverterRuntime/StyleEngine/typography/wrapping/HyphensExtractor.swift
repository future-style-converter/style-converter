//
//  HyphensExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6.
//

import Foundation

enum HyphensProperty { static let name = "Hyphens" }

enum HyphensExtractor {
    static func extract(from properties: [IRProperty]) -> HyphensConfig? {
        var cfg = HyphensConfig()
        var touched = false
        for prop in properties where prop.type == HyphensProperty.name {
            touched = true
            cfg.mode = ValueExtractors.extractKeyword(prop.data)?.lowercased()
        }
        return touched ? cfg : nil
    }
}
