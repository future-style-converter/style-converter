//
//  TableExtractor.swift
//  StyleEngine/table — Phase 10.
//

import Foundation

enum TableProperty {
    /// 5 entries — mirrors the `table/` parser folder.
    static let names: [String] = [
        "BorderCollapse", "BorderSpacing", "CaptionSide",
        "EmptyCells", "TableLayout",
    ]
    static var set: Set<String> { Set(names) }
}

enum TableExtractor {
    static func extract(from properties: [IRProperty]) -> TableConfig? {
        var cfg = TableConfig()
        let owned = TableProperty.set
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
