package com.styleconverter.runtime.table

// wave-54 lane L2 (TB-android) — the CHROME of an ANONYMOUS table: the one
// table box this runtime fabricates itself (TableBodyForest's synthetic
// `#table` → `#row` → `#cell<k>` for a `display: table` body, CSS 2.1 §17.2.1
// rule 2). Two Compose table-renderer behaviours are wrong on it, and both
// were traced to the pixel on wave53-probe CSS2/css21-errata/s-11-1-1b-006
// android (tools/titan/results/wave54-plan/compose-table-body-cell.md §3):
//
//  D1 — the demo stroke. ComponentRenderer's `fabricatedCellBorder`
//       expression is TRUE for a table that DECLARES `Display` and has no
//       `_tag` — exactly the synthetic table — so TableApplier.TableCell
//       painted its 1-dp black "demo default" (NO CSS basis, see
//       TableApplier.LocalTableFabricatedCellBorder) around the empty
//       anonymous cell: the 76-px outline at x 24-43, rows 56-75.
//       §17.2.1: anonymous boxes carry no border (`border` is not inherited);
//       §17.6.1: in the separated model a cell paints only its OWN borders.
//  D2 — the first cell takes the block fill. The anonymous cell, first in
//       the `fillMaxWidth` TableRow, routes to the BLOCK path whose
//       composed-WPT `blockFlowWidth` is `fillMaxWidth()`, so it took the
//       whole 20-px shrink-to-fit table width; the td was then measured at
//       max-width 0 and ExactWidthOverflow painted its 20-px fill
//       start-anchored past its 0-wide slot (x 44-63). §17.5.2.2 (auto
//       layout, room available): each column gets its MAX-CONTENT width —
//       0 for the empty anonymous cell, 20 for the td.
//
// SCOPE — keyed on ONE marker only the forest creates
// (TableBodyForest.ANONYMOUS_ROLE): 1 of the 1435 corpus documents
// (s-11-1-1b-006). Every other table gets `Chrome(stroke = today's
// expression, hug = false)` and TableApplier's frozen defaults, so its
// layout is byte-identical by construction. The general versions (every
// declared table loses the stroke / every cell hugs) move 12 D1 + 8 D2
// documents and are queued, not done here (PLAN.md §2 L2, brief §5).
//
// iOS needs none of this (TableSeparatedLayout sizes each cell at its ideal
// size and paints no demo stroke); web lets Chrome run §17.2.1 itself.

import androidx.compose.runtime.compositionLocalOf                  // the per-table hug switch
import androidx.compose.ui.Modifier                                    // hugColumn's receiver
import com.styleconverter.runtime.core.ir.IRComponent                  // chrome's input (the TABLE-routed box)
import com.styleconverter.runtime.layout.IntrinsicChannel              // the guarded max-content read

object TableCellHug {

    /**
     * Do the current table's cells take their column's max-content width
     * (§17.5.2.2) instead of the BLOCK path's fill? Provided per table by
     * `TableApplier.Table(cellsHugContent = …)`, so a nested table resets it
     * to its own value. `false` is the frozen behaviour of every table.
     */
    val LocalTableCellsHugContent = compositionLocalOf { false }

    /**
     * The two chrome decisions for one table box at the renderer's
     * `TableApplier.Table(…)` call.
     *
     * @property stroke may the separated-model cells paint the applier's
     *   demo default stroke (TableApplier.Table's `fabricatedCellBorder`).
     * @property hug do the cells size at max-content (`cellsHugContent`).
     */
    data class Chrome(val stroke: Boolean, val hug: Boolean)

    /**
     * Pure (component + one Boolean in, two Booleans out) so the whole
     * decision is pinned on the JVM against verbatim corpus payloads
     * (TableCellHugTest) — the TableBoxTree "extract the decision" idiom.
     *
     * @param component the box the renderer routed to `DisplayType.TABLE`.
     * @param fabricatedDefault the renderer's frozen `fabricatedCellBorder`
     *   expression, passed VERBATIM by the call site so its every existing
     *   value survives for every non-anonymous table.
     */
    fun chrome(component: IRComponent, fabricatedDefault: Boolean): Chrome {
        // The only box this runtime fabricates as a table (TableBodyForest).
        val anonymous = TableBodyForest.isAnonymous(component)
        return Chrome(
            // D1: §17.2.1 — an anonymous table's cells carry no border.
            stroke = fabricatedDefault && !anonymous,
            // D2: §17.5.2.2 — its columns take their max-content widths.
            hug = anonymous,
        )
    }

    /** Logged ONCE by IntrinsicChannel.probe when a cell subtree refuses the intrinsic read. */
    internal const val CELL_HUG_REFUSAL =
        "CSS 2.1 §17.5.2 column max-content skipped — this anonymous cell's " +
            "subtree has no intrinsic channel; the cell keeps the fill."
}

/**
 * Size a table cell at its max-content width when [enabled] (§17.5.2.2 auto
 * layout with room: each column gets its max-content width); otherwise the
 * receiver ITSELF, so a non-hugging table's modifier chain is the frozen one
 * node-for-node.
 *
 * Built on [IntrinsicChannel.widthAtMaxIntrinsic] — the same guarded
 * primitive TableApplier.Table already spends on the table's own
 * shrink-to-fit width (whose read on wave53-probe answered 20 = 0 + 20 for
 * this very subtree). The incoming Row bound still clamps the result, so
 * the used width is `min(max-content, remaining)`; on refusal (a
 * SubcomposeLayout below the cell) the cell keeps the fill, logged once
 * with [TableCellHug.CELL_HUG_REFUSAL] — never a dead capture.
 */
fun Modifier.hugColumn(enabled: Boolean): Modifier =
    if (!enabled) this                                                      // frozen chain, no extra node
    else with(IntrinsicChannel) {                                           // member-extension scope
        this@hugColumn.widthAtMaxIntrinsic(                                 // Modifier.width(IntrinsicSize.Max), guarded
            logTag = "TableCellHug",                                        // logcat points at THIS mechanism
            refusalContext = TableCellHug.CELL_HUG_REFUSAL,                 // what was skipped, what is kept
        )
    }
