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
    // Wave-9 regression fix — typed `column-width` (css-multicol-1 §3.2)
    // in RESOLVED px. Nil for `auto` / absent / non-absolute values
    // (em/%/calc keep their raw string in rawByType — a definite basis
    // is required by the §3 used-value math, so an unresolvable width
    // honestly behaves as `auto` here). Feeds MulticolMath.usedColumns
    // so a multicol container's children fill their COLUMN box, not the
    // whole container (css-multicol-1 §2: the containing block of a
    // multicol element's children is the column box).
    var widthPx: Double? = nil

    // True when either typed multicol input is non-auto — css-multicol-1
    // §2: an element whose computed column-width OR column-count is not
    // `auto` establishes a multicol formatting context. Rule properties
    // alone (column-rule-*, column-fill, column-span) do NOT.
    var isMulticolContainer: Bool { count != nil || widthPx != nil }
}
