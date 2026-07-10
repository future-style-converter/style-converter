//
//  ColumnsExtractor.swift
//  StyleEngine/columns — Phase 10.
//

import Foundation

enum ColumnsProperty {
    /// 6 entries — ColumnGap deliberately omitted (owned by GapProperty).
    static let names: [String] = [
        "ColumnCount", "ColumnWidth", "ColumnFill", "ColumnSpan",
        "ColumnRuleStyle", "ColumnRuleWidth", "ColumnRuleColor",
    ]
    static var set: Set<String> { Set(names) }
}

enum ColumnsExtractor {
    static func extract(from properties: [IRProperty]) -> ColumnsConfig? {
        var cfg = ColumnsConfig()
        let owned = ColumnsProperty.set
        for p in properties where owned.contains(p.type) {
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
            // Fidelity wave 3 — typed column-count. Wire shape (see
            // converter ColumnCountSerializer): `auto` → the string
            // "auto"/"AUTO"; an integer count → a bare JSON number
            // ({"type":"ColumnCount","data":3.0}). Only counts ≥ 1 are
            // valid per css-multicol-1 §3.1 (<integer [1,∞]>).
            if p.type == "ColumnCount", let n = p.data.doubleValue, n >= 1 {
                cfg.count = Int(n)
            }
        }
        return cfg.touched ? cfg : nil
    }
}
