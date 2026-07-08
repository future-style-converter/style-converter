//
//  PerformanceConfig.swift
//  StyleEngine/performance — Phase 10.
//
//  Phase 10 performance-hint family (distinct from Phase 4 Isolation
//  which lives in IsolationConfig/Extractor/Applier beside this file):
//  contain, content-visibility, will-change, contain-intrinsic-*.
//
//  iOS has no direct analog for containment or will-change.
//  `.drawingGroup()` is the closest rasterise-to-bitmap hint but too
//  risky to apply by default (it changes compositing semantics).
//

import Foundation

struct PerformanceConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
