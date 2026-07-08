//
//  FontStretchExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//
//  Keyword → percentage table (per CSS Fonts Module 4 §5.2). Also accepts
//  a direct `{ percent: N }` blob when the parser resolved a %-value.
//

import Foundation

enum FontStretchProperty { static let name = "FontStretch" }

enum FontStretchExtractor {
    static func extract(from properties: [IRProperty]) -> FontStretchConfig? {
        var cfg = FontStretchConfig()
        var touched = false
        for prop in properties where prop.type == FontStretchProperty.name {
            touched = true
            // Keyword form first — maps 9 named widths.
            if let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased() {
                cfg.percent = keywordToPercent(kw)
                continue
            }
            // Percent form: { percentage: 75 } or { original: { u: PERCENT, v: 75 } }.
            if case .object(let o) = prop.data {
                if let pct = (o["percentage"]?.doubleValue ?? o["percent"]?.doubleValue) {
                    cfg.percent = CGFloat(pct)
                } else if let orig = o["original"]?.objectValue,
                          let u = orig["u"]?.stringValue, u.uppercased() == "PERCENT",
                          let v = orig["v"]?.doubleValue {
                    cfg.percent = CGFloat(v)
                }
            }
        }
        return touched ? cfg : nil
    }

    // CSS Fonts §5.2 keyword → percent width. Values taken from the spec.
    private static func keywordToPercent(_ kw: String) -> CGFloat? {
        switch kw {
        case "ultra-condensed": return 50
        case "extra-condensed": return 62.5
        case "condensed":       return 75
        case "semi-condensed":  return 87.5
        case "normal":          return 100
        case "semi-expanded":   return 112.5
        case "expanded":        return 125
        case "extra-expanded":  return 150
        case "ultra-expanded":  return 200
        default:                return nil
        }
    }
}
