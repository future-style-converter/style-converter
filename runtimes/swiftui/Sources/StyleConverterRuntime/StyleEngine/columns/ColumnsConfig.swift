//
//  ColumnsConfig.swift
//  StyleEngine/columns — Phase 10.
//
//  Multi-column layout (column-count, column-width, column-gap-NOT-
//  owned-here-see-spacing-GapExtractor, column-rule-*, column-span,
//  column-fill). SwiftUI has no multi-column layout — nothing maps.
//
//  NOTE: ColumnGap lives in the Phase 2 spacing family (GapExtractor)
//  and is intentionally NOT re-claimed here. (Retro P2b: the name used to
//  be the `GapProperty` enum, deleted as zero-reference — A6#10.)
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

    // Wave-43 lane V6 — css-overflow-4 §5.3 `continue: discard`: content
    // laid out past the multicol box's last column (the §8.2 overflow
    // columns) is DISCARDED instead of painted. Only the DISCARD keyword
    // flips this; `auto` (and any future keyword) keeps normal overflow.
    // Mirrors Compose's MultiColumnConfig.continueDiscard byte-for-byte:
    // the flag rides the columns config into MulticolGreedyLayout, which
    // threads it into MulticolSpannerFlow.plan's discardOverflow (the
    // wave-42 plan machinery both natives already share and pin).
    // Wave-43 lane G3 made that parity claim true for the FOLD as well:
    // both extractors now take the LAST `Continue` declaration in the
    // cascade (css-cascade-5 §6.4.4), so a duplicated declaration such as
    // [DISCARD, AUTO] resolves false on both platforms. It used to resolve
    // true here (an any-DISCARD-wins `contains` scan) and false on Compose.
    var continueDiscard: Bool = false

    // Wave-45 lane X3 — typed `column-fill` (css-multicol-1 §7.1): true
    // iff the computed value is `auto` (sequential fill); false is the
    // INITIAL `balance` (§7.1) and every other/absent keyword. The float
    // strip needs the distinction because the two modes produce different
    // used column block-sizes (fill keeps the definite H; balance reduces
    // to ceil(C/N)) — the same `config.fill == ColumnFill.AUTO` read the
    // Compose twin threads into its strip halves (MultiColumnApplier).
    var fillAuto: Bool = false

    // True when either typed multicol input is non-auto — css-multicol-1
    // §2: an element whose computed column-width OR column-count is not
    // `auto` establishes a multicol formatting context. Rule properties
    // alone (column-rule-*, column-fill, column-span) do NOT.
    var isMulticolContainer: Bool { count != nil || widthPx != nil }
}
