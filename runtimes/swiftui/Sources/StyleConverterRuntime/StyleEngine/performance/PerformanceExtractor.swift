//
//  PerformanceExtractor.swift
//  StyleEngine/performance — Phase 10.
//
//  NOTE: `Isolation` is owned by IsolationExtractor (Phase 4) and is
//  NOT re-claimed here. (Retro P2b: the name used to be the
//  `IsolationProperty` enum, deleted as zero-reference — A6#10.)
//

import Foundation

enum PerformanceProperty {
    /// 8 entries — contain + will-change + contain-intrinsic-{size,
    /// width, height, block-size, inline-size} + content-visibility
    /// (content-visibility's parser and IR model live under `rendering/`,
    /// and its fixture variants are in
    /// `fixtures/properties/rendering/longtail.json`, but this registry
    /// claims it under performance because `contain`-family containment is
    /// where it belongs behaviourally — css-contain-2 §4 "Suppressing An
    /// Element's Contents Entirely: the content-visibility property", which
    /// defines it in terms of the same size/layout/paint containment set
    /// `contain` grants. Retro P2e
    /// rewrote this parenthetical: it said "lives under rendering/, but
    /// README-phase10 lists it in rendering", a tautology pointing at an
    /// index the 2026-07-08 restructure (commit 02e4c457) had deleted.)
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
