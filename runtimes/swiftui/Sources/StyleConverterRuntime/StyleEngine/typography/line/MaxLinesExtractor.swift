//
//  MaxLinesExtractor.swift
//  StyleEngine/typography/line — Phase 6.
//

import Foundation

enum MaxLinesProperty { static let name = "MaxLines" }

enum MaxLinesExtractor {
    static func extract(from properties: [IRProperty]) -> MaxLinesConfig? {
        var cfg = MaxLinesConfig()
        var touched = false
        for prop in properties where prop.type == MaxLinesProperty.name {
            touched = true
            // `none` disables the cap; integer ≥ 1 sets it.
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "none" {
                cfg.lines = nil; continue
            }
            if let n = ValueExtractors.extractInt(prop.data), n >= 1 {
                cfg.lines = n
            }
        }
        return touched ? cfg : nil
    }
}
