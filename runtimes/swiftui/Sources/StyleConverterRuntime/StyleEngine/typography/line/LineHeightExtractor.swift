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
            // Two shapes reach this extractor: `{ "px": N }` for
            // resolved lengths and `{ "multiplier": N, "original": … }`
            // for unitless multipliers (CSS `line-height: 2`) and
            // percentages. NOTE the converter actually ships lengths on
            // a NESTED wire with no top-level px
            // (`{"original":{"type":"length", …}}`, pinned live) —
            // DynamicValueResolver.resolveLineHeightWire unwraps that
            // to `{px:N}` BEFORE extraction, because em needs the
            // element's own font size (css-values-4 §6.1), a channel
            // only the resolver has. extractPx reads the px form; the
            // multiplier path needs an explicit lookup so it isn't
            // silently dropped.
            cfg.px = ValueExtractors.extractPx(prop.data)
            if cfg.px == nil, case .object(let o) = prop.data,
               let mult = o["multiplier"]?.doubleValue {
                cfg.multiplier = CGFloat(mult)
            }
        }
        return touched ? cfg : nil
    }
}
