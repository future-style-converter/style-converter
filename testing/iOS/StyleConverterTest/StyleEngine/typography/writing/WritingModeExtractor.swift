//
//  WritingModeExtractor.swift
//  StyleEngine/typography/writing — Phase 6.
//

import Foundation

enum WritingModeProperty { static let name = "WritingMode" }

enum WritingModeExtractor {
    static func extract(from properties: [IRProperty]) -> WritingModeConfig? {
        var cfg = WritingModeConfig()
        var touched = false
        for prop in properties where prop.type == WritingModeProperty.name {
            touched = true
            let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased() ?? ""
            cfg.isVertical = kw.hasPrefix("vertical") || kw.hasPrefix("sideways")
        }
        return touched ? cfg : nil
    }
}
