//
//  TextDecorationLineExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationLineProperty { static let name = "TextDecorationLine" }

enum TextDecorationLineExtractor {
    static func extract(from properties: [IRProperty]) -> TextDecorationLineConfig? {
        var cfg = TextDecorationLineConfig()
        var touched = false
        for prop in properties where prop.type == TextDecorationLineProperty.name {
            touched = true
            // Gather keywords from string, array-of-strings, or object forms.
            let tokens = gatherTokens(prop.data)
            // Reset per-prop so later occurrences cleanly override.
            cfg = TextDecorationLineConfig()
            for tok in tokens {
                switch tok {
                case "underline":    cfg.underline = true
                case "overline":     cfg.overline = true
                case "line-through": cfg.lineThrough = true
                case "blink":        cfg.blink = true
                default: break    // `none` / unknown → no flags
                }
            }
        }
        return touched ? cfg : nil
    }

    // Normalise the various IR shapes to a flat list of lower-cased tokens.
    private static func gatherTokens(_ v: IRValue) -> [String] {
        if let s = v.stringValue {
            return s.lowercased().split(separator: " ").map(String.init)
        }
        if case .array(let entries) = v {
            return entries.compactMap { $0.stringValue?.lowercased() }
        }
        if case .object(let o) = v, let kw = o["keyword"]?.stringValue {
            return [kw.lowercased()]
        }
        return []
    }
}
