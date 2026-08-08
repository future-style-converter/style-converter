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
            // Wave 35 (lane B5) — keep the keyword too. Parsed through the
            // shared `WritingModeValue.from` so the wire's `VERTICAL_RL` and
            // the CSS `vertical-rl` spelling land on the same case, and an
            // unknown keyword bottoms out at `.horizontalTb` exactly as the
            // `isVertical` read above bottoms out at false.
            cfg.mode = WritingModeValue.from(keyword: kw)
        }
        return touched ? cfg : nil
    }
}
