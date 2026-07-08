//
//  OutlineExtractor.swift
//  StyleEngine/borders/outline — Phase 5.
//
//  Reads the four outline-* properties from a property list and returns
//  an OutlineConfig. Widths accept either `{"px": N}` (pre-resolved) or
//  the `{type: "keyword", value: "THIN|MEDIUM|THICK"}` shape the CSS
//  parser emits for bare keywords (see parser file
//  `src/main/kotlin/app/parsing/css/properties/longhands/effects/outline/OutlineWidthPropertyParser.kt`).
//

import Foundation
import CoreGraphics

// Same contract as the other extractors.
enum OutlineExtractor {

    // Registered-property list for `PropertyRegistry.migrated`.
    static let propertyNames: [String] = [
        "OutlineWidth", "OutlineStyle", "OutlineColor", "OutlineOffset",
    ]

    // Returns nil when no outline-* key is present. Callers short-circuit.
    static func extract(from properties: [IRProperty]) -> OutlineConfig? {
        var cfg = OutlineConfig()
        var touched = false
        for prop in properties {
            switch prop.type {
            case "OutlineWidth":
                cfg.width = extractOutlineWidth(prop.data); touched = true
            case "OutlineStyle":
                cfg.style = extractBorderStyle(prop.data) ?? .none; touched = true
            case "OutlineColor":
                // Nil result = currentColor — the applier uses .primary.
                cfg.color = ValueExtractors.extractColor(prop.data); touched = true
            case "OutlineOffset":
                // Offset is a plain length in the IR — pre-resolved to px.
                cfg.offset = ValueExtractors.extractPx(prop.data) ?? 0; touched = true
            default: break
            }
        }
        return touched ? cfg : nil
    }

    // Width keywords → px. CSS spec values match the ones Chrome /
    // Android use so cross-platform baselines are comparable.
    private static func extractOutlineWidth(_ v: IRValue?) -> CGFloat {
        if let px = ValueExtractors.extractPx(v) { return px }
        // Keyword variant — `{ type: "keyword", value: "THIN" }`.
        let kw = ValueExtractors.extractKeyword(v)?.uppercased() ?? ""
        switch kw {
        case "THIN":   return 1
        case "MEDIUM": return 3
        case "THICK":  return 5
        default:       return 0
        }
    }
}
