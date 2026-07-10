//
//  ColumnsConfig.swift
//  StyleEngine/columns — Phase 10.
//
//  Multi-column layout (column-count, column-width, column-gap-NOT-
//  owned-here-see-spacing-GapExtractor, column-rule-*, column-span,
//  column-fill). SwiftUI has no multi-column layout — nothing maps.
//
//  NOTE: ColumnGap lives in the Phase 2 spacing family (GapProperty)
//  and is intentionally NOT re-claimed here.
//

import Foundation

struct ColumnsConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
    // Fidelity wave 3 — typed `column-count` (css-multicol-1 §3.1).
    // Nil for `auto` / absent. Used by ComponentRenderer to give an
    // auto-width multicol container the block-level full-width default
    // the web reference renders (Columns_Decorated: N × max-content +
    // gaps blows past the canvas, so the web harness's max-width: 100%
    // cap makes the box exactly containing-block wide).
    var count: Int? = nil
}
