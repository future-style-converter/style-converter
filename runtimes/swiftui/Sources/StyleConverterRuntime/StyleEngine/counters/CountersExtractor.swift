//
//  CountersExtractor.swift
//  StyleEngine/counters — Phase 10.
//

import Foundation

enum CountersProperty {
    static let names: [String] = [
        "CounterIncrement", "CounterReset", "CounterSet",
    ]
    static var set: Set<String> { Set(names) }
}

enum CountersExtractor {
    static func extract(from properties: [IRProperty]) -> CountersConfig? {
        var cfg = CountersConfig()
        let owned = CountersProperty.set
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
