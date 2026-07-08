//
//  UnsupportedPagingExtractor.swift
//  StyleEngine/paging — Phase 10.
//

import Foundation

enum UnsupportedPagingProperty {
    /// 7 entries — break + page-break longhands + margin-break.
    static let names: [String] = [
        "BreakAfter", "BreakBefore", "BreakInside",
        "PageBreakAfter", "PageBreakBefore", "PageBreakInside",
        "MarginBreak",
    ]
    static var set: Set<String> { Set(names) }
}

enum UnsupportedPagingExtractor {
    static func extract(from properties: [IRProperty]) -> UnsupportedPagingConfig? {
        var cfg = UnsupportedPagingConfig()
        let owned = UnsupportedPagingProperty.set
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
