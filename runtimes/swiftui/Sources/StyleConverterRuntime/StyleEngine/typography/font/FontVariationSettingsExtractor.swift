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
            // Array of { tag, value } objects — parser output.
            if case .array(let entries) = prop.data {
                cfg.axes = entries.compactMap { e in
                    guard case .object(let o) = e,
                          let tag = o["tag"]?.stringValue,
                          let v = (o["value"] ?? o["numeric"])?.doubleValue
                    else { return nil }
                    return FontVariationAxis(tag: tag, value: v)
                }
            }
        }
        return touched ? cfg : nil
    }
}
