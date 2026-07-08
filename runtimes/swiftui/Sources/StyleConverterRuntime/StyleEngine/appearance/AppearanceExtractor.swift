//
//  AppearanceExtractor.swift
//  StyleEngine/appearance — Phase 10.
//

import Foundation

enum AppearanceProperty {
    /// 5 entries — AccentColor deliberately omitted (Phase 4 owns it).
    static let names: [String] = [
        "Appearance", "AppearanceVariant",
        "ColorAdjust", "ColorScheme",
        "ImageRenderingQuality",
    ]
    static var set: Set<String> { Set(names) }
}

enum AppearanceExtractor {
    static func extract(from properties: [IRProperty]) -> AppearanceConfig? {
        var cfg = AppearanceConfig()
        let owned = AppearanceProperty.set
        for p in properties where owned.contains(p.type) {
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
        }
        return cfg.touched ? cfg : nil
    }
}
