//
//  FontFeatureSettingsExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//

import Foundation

enum FontFeatureSettingsProperty { static let name = "FontFeatureSettings" }

enum FontFeatureSettingsExtractor {
    static func extract(from properties: [IRProperty]) -> FontFeatureSettingsConfig? {
        var cfg = FontFeatureSettingsConfig()
        var touched = false
        for prop in properties where prop.type == FontFeatureSettingsProperty.name {
            touched = true
            // Normal/none → empty list.
            if let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased(),
               kw == "normal" || kw == "none" {
                cfg.features = []
                continue
            }
            // Array of { tag, value } objects.
            if case .array(let entries) = prop.data {
                cfg.features = entries.compactMap { e in
                    guard case .object(let o) = e,
                          let tag = o["tag"]?.stringValue else { return nil }
                    let value = (o["value"]?.intValue) ?? 1
                    return FontFeatureTag(tag: tag, value: value)
                }
            }
        }
        return touched ? cfg : nil
    }
}
