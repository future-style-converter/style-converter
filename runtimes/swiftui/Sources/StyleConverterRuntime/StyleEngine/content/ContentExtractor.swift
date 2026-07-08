//
//  ContentExtractor.swift
//  StyleEngine/content — Phase 10.
//

import Foundation

enum ContentProperty {
    static let names: [String] = ["Content"]
    static var set: Set<String> { Set(names) }
}

enum ContentExtractor {
    static func extract(from properties: [IRProperty]) -> ContentConfig? {
        var cfg = ContentConfig()
        let owned = ContentProperty.set
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
