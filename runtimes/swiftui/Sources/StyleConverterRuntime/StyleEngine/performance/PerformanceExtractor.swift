//
//  PerformanceExtractor.swift
//  StyleEngine/performance — Phase 10.
//
//  NOTE: `Isolation` is owned by IsolationProperty (Phase 4) and is
//  NOT re-claimed here.
//

import Foundation

enum PerformanceProperty {
    /// 8 entries — contain + will-change + contain-intrinsic-{size,
    /// width, height, block-size, inline-size} + content-visibility
    /// (parser-folder-wise content-visibility lives under rendering/,
    /// but README-phase10 lists it in rendering; we keep it there).
    static let names: [String] = [
        "Contain", "WillChange",
        "ContainIntrinsicSize",
        "ContainIntrinsicWidth", "ContainIntrinsicHeight",
        "ContainIntrinsicBlockSize", "ContainIntrinsicInlineSize",
    ]
    static var set: Set<String> { Set(names) }
}

enum PerformanceExtractor {
    static func extract(from properties: [IRProperty]) -> PerformanceConfig? {
        var cfg = PerformanceConfig()
        let owned = PerformanceProperty.set
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
