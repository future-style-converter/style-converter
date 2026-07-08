//
//  UnsupportedMathExtractor.swift
//  StyleEngine/math — Phase 10.
//

import Foundation

enum UnsupportedMathProperty {
    static let names: [String] = [
        "MathStyle", "MathShift", "MathDepth",
    ]
    static var set: Set<String> { Set(names) }
}

enum UnsupportedMathExtractor {
    static func extract(from properties: [IRProperty]) -> UnsupportedMathConfig? {
        var cfg = UnsupportedMathConfig()
        let owned = UnsupportedMathProperty.set
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
