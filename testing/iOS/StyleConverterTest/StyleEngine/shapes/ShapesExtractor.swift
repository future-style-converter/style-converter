//
//  ShapesExtractor.swift
//  StyleEngine/shapes — Phase 10.
//

import Foundation

enum ShapesProperty {
    /// 5 entries — mirrors the `shapes/` parser folder.
    static let names: [String] = [
        "ShapeOutside", "ShapeMargin", "ShapePadding",
        "ShapeImageThreshold", "ShapeInside",
    ]
    static var set: Set<String> { Set(names) }
}

enum ShapesExtractor {
    static func extract(from properties: [IRProperty]) -> ShapesConfig? {
        var cfg = ShapesConfig()
        let owned = ShapesProperty.set
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
