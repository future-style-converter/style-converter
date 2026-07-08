//
//  ListsExtractor.swift
//  StyleEngine/lists — Phase 10.
//

import Foundation

enum ListsProperty {
    /// 3 entries — Quotes deliberately omitted (Phase 6 typography).
    static let names: [String] = [
        "ListStyleType", "ListStylePosition", "ListStyleImage",
    ]
    static var set: Set<String> { Set(names) }
}

enum ListsExtractor {
    static func extract(from properties: [IRProperty]) -> ListsConfig? {
        var cfg = ListsConfig()
        let owned = ListsProperty.set
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
