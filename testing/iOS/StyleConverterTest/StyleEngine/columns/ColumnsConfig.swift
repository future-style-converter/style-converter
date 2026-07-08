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
}
