//
//  FontVariationSettingsExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//

import Foundation

enum FontVariationSettingsProperty { static let name = "FontVariationSettings" }

enum FontVariationSettingsExtractor {
    static func extract(from properties: [IRProperty]) -> FontVariationSettingsConfig? {
        var cfg = FontVariationSettingsConfig()
        var touched = false
        for prop in properties where prop.type == FontVariationSettingsProperty.name {
            touched = true
            // `normal` → empty axis list.
            if let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased(),
               kw == "normal" { cfg.axes = []; continue }
            // Array of { tag, value } objects — legacy parser output.
            if case .array(let entries) = prop.data {
                cfg.axes = entries.compactMap { Self.axis(from: $0) }
            }
            // Fidelity wave 2 — the current converter emits the wrapped
            // object form `{ "type": "variations", "variations": [
            // { "axis": "wght", "value": 900 } ] }` (see out/ir-typography
            // Typography_C07). Without this branch the axis list stayed
            // empty and `"wght" 900` silently dropped on iOS.
            if case .object(let o) = prop.data,
               case .array(let entries)? = o["variations"] {
                cfg.axes = entries.compactMap { Self.axis(from: $0) }
            }
        }
        return touched ? cfg : nil
    }

    /// One `<axis-tag> <number>` pair from either IR spelling: the legacy
    /// `{ tag, value }` or the current `{ axis, value }` object shape.
    private static func axis(from e: IRValue) -> FontVariationAxis? {
        guard case .object(let o) = e,
              // `tag` (legacy) or `axis` (current converter) name key.
              let tag = (o["tag"] ?? o["axis"])?.stringValue,
              let v = (o["value"] ?? o["numeric"])?.doubleValue
        else { return nil }
        return FontVariationAxis(tag: tag, value: v)
    }
}
