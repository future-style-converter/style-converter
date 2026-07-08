//
//  GlobalExtractor.swift
//  StyleEngine/global — Phase 10.
//

import Foundation

enum GlobalProperty {
    static let names: [String] = ["All"]
    static var set: Set<String> { Set(names) }
}

enum GlobalExtractor {
    static func extract(from properties: [IRProperty]) -> GlobalConfig? {
        var cfg = GlobalConfig()
        let owned = GlobalProperty.set
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
