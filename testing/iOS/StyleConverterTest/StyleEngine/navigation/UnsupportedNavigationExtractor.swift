//
//  UnsupportedNavigationExtractor.swift
//  StyleEngine/navigation — Phase 10.
//

import Foundation

enum UnsupportedNavigationProperty {
    /// 5 entries — mirrors `app/parsing/.../navigation/`.
    static let names: [String] = [
        "NavUp", "NavDown", "NavLeft", "NavRight", "ReadingOrder",
    ]
    static var set: Set<String> { Set(names) }
}

enum UnsupportedNavigationExtractor {
    static func extract(from properties: [IRProperty]) -> UnsupportedNavigationConfig? {
        var cfg = UnsupportedNavigationConfig()
        let owned = UnsupportedNavigationProperty.set
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
