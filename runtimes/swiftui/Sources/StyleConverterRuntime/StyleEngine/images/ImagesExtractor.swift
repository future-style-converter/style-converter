//
//  ImagesExtractor.swift
//  StyleEngine/images — Phase 10.
//

import Foundation

enum ImagesProperty {
    /// 4 entries — mirrors `app/parsing/.../images/`.
    static let names: [String] = [
        "ImageRendering", "ObjectFit", "ObjectPosition", "ObjectViewBox",
    ]
    static var set: Set<String> { Set(names) }
}

enum ImagesExtractor {
    static func extract(from properties: [IRProperty]) -> ImagesConfig? {
        var cfg = ImagesConfig()
        let owned = ImagesProperty.set
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
