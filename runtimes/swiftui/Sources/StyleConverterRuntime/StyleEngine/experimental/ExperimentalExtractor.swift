//
//  ExperimentalExtractor.swift
//  StyleEngine/experimental — Phase 10.
//

import Foundation

enum ExperimentalProperty {
    static let names: [String] = [
        "PresentationLevel", "Running", "StringSet",
    ]
    static var set: Set<String> { Set(names) }
}

enum ExperimentalExtractor {
    static func extract(from properties: [IRProperty]) -> ExperimentalConfig? {
        var cfg = ExperimentalConfig()
        let owned = ExperimentalProperty.set
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
