//
//  UnsupportedPrintExtractor.swift
//  StyleEngine/print — Phase 10.
//

import Foundation

enum UnsupportedPrintProperty {
    /// 11 entries — mirrors the `print/` parser folder + Bleed/Marks/
    /// Size from the @page descriptor set.
    static let names: [String] = [
        "Bleed",
        "BookmarkLabel", "BookmarkLevel", "BookmarkState", "BookmarkTarget",
        "FootnoteDisplay", "FootnotePolicy",
        "Leader", "Marks", "Page", "Size",
    ]
    static var set: Set<String> { Set(names) }
}

enum UnsupportedPrintExtractor {
    static func extract(from properties: [IRProperty]) -> UnsupportedPrintConfig? {
        var cfg = UnsupportedPrintConfig()
        let owned = UnsupportedPrintProperty.set
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
