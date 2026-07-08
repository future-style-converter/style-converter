//
//  LineHeightExtractor.swift
//  StyleEngine/typography/line — Phase 6.
//

import Foundation

enum LineHeightProperty { static let name = "LineHeight" }

enum LineHeightExtractor {
    static func extract(from properties: [IRProperty]) -> LineHeightConfig? {
        var cfg = LineHeightConfig()
        var touched = false
        for prop in properties where prop.type == LineHeightProperty.name {
            touched = true
            // The IR emits LineHeight as `{ "px": N }` for absolute
            // lengths and `{ "multiplier": N, "original": ... }` for
            // unitless multipliers (CSS `line-height: 2`). extractPx
            // only sees the `px` form; the multiplier path needs an
            // explicit lookup so it isn't silently dropped.
            cfg.px = ValueExtractors.extractPx(prop.data)
            if cfg.px == nil, case .object(let o) = prop.data,
               let mult = o["multiplier"]?.doubleValue {
                cfg.multiplier = CGFloat(mult)
            }
        }
        return touched ? cfg : nil
    }
}
