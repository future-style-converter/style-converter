//
//  TableConfig.swift
//  StyleEngine/table — Phase 10.
//
//  HTML-table CSS (border-collapse, border-spacing, caption-side,
//  empty-cells, table-layout). SwiftUI has no HTML-table semantics —
//  Grid is the closest primitive but doesn't implement collapse/
//  spacing. Identity until a dedicated table renderer ships.
//

import Foundation

struct TableConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
