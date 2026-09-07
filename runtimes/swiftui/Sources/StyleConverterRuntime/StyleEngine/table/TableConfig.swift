//
//  TableConfig.swift
//  StyleEngine/table — Phase 10.
//
//  HTML-table CSS (border-collapse, border-spacing, caption-side,
//  empty-cells, table-layout).
//
//  THIS AGGREGATE is identity — nothing reads `rawByType`, there is no
//  TableApplier, and TableExtractor exists so PropertyRegistry (:194,
//  `.union(TableProperty.set)`) can claim the five names and StyleBuilder
//  skips them. Retro P2e (finding A6#15, phrase sweep) corrected the
//  sentence that followed from that ("SwiftUI has no HTML-table semantics
//  … Identity until a dedicated table renderer ships"): iOS DOES render
//  tables since wave 34 (lane T), just not through this struct.
//  `border-spacing` and `border-collapse` are read straight off the
//  IRProperty list by TableSeparatedTracks (CSS 2.1 §17.6.1 track model,
//  nil under `collapse`) and drawn by Renderer/TableSeparatedLayout, and
//  TableBoxTree.roleOf drives the css-tables-3 §2.1 role classification
//  plus the shrink-to-fit box in ComponentRenderer. What genuinely has no
//  path on iOS is the §17.6.2 COLLAPSING border model and the three
//  properties with zero readers anywhere in Sources/: caption-side,
//  empty-cells, table-layout.
//

import Foundation

struct TableConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
