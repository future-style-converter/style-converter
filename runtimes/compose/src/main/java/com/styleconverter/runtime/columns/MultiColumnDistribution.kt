package com.styleconverter.runtime.columns

/**
 * The greedy multi-child column distribution — extracted PURE from the
 * measure pass of [MultiColumnApplier]'s MultiColumnDistributionLayout
 * (lane ios-multichild-multicol) with byte-identical semantics, so that:
 *
 *  1. the JVM suite ([MultiColumnDistributionTest]) pins the exact
 *     per-child column assignments without a compose surface, and
 *  2. the iOS runtime can mirror the ALGORITHM — not the spec — for
 *     native-pair parity (swiftui `MulticolDistribution.swift` shares
 *     the same D-table pins in MulticolDistributionTests).
 *
 * ## The algorithm (pre-wave-10 machinery, unchanged)
 *
 * css-multicol-1 §7.1 column balancing is APPROXIMATED by a greedy
 * min-height heuristic, not implemented to spec (see
 * MultiColumnApplier.Notes.COLUMN_BALANCING):
 *
 *  - children are visited in child (composition) order;
 *  - each child lands in the column with the smallest running height;
 *  - ties break to the LOWEST column index (Kotlin's `minByOrNull`
 *    returns the first minimal element — the tie-break is part of the
 *    cross-platform contract, D3 in the shared pin table);
 *  - the child's block offset is that column's running height BEFORE
 *    placement (children butt together — no inter-item gap, matching
 *    CSS block stacking inside a column box);
 *  - the container's block-size is the tallest resulting column.
 *
 * This is order-dependent and NOT optimal balancing (D4 pins a case
 * where greedy yields 70 where true balancing would yield 60) — the
 * cross-platform gate is Android parity, so both runtimes pin the
 * greedy answer on purpose.
 */
object MultiColumnDistribution {

    /**
     * One child's resolved placement: which column box it landed in and
     * its block offset from the column's top, in px. The inline (x)
     * position is NOT part of the distribution — it derives from the
     * used column geometry (columnIndex * (width + gap)) at the layout
     * call site, exactly as the pre-extraction loop computed it.
     */
    data class Slot(
        /** 0-based used-column index the child was assigned to. */
        val columnIndex: Int,
        /** Block offset from the column top — the column's height BEFORE this child. */
        val yOffsetPx: Int
    )

    /**
     * Distribute children (given by their measured block-sizes, in child
     * order) into [columnCount] columns per the greedy rule above.
     *
     * @param childHeightsPx each child's measured height in px, in the
     *   exact order the layout received its measurables (order matters —
     *   the heuristic is order-dependent by design).
     * @param columnCount the USED column count (resolveUsedColumns
     *   guarantees >= 1 at the layout call site; coerced defensively so
     *   a direct caller can never index an empty height array).
     * @return one [Slot] per child, index-aligned with the input.
     */
    fun distribute(childHeightsPx: List<Int>, columnCount: Int): List<Slot> {
        // Defensive floor — the layout always passes used.count >= 1, but a
        // pure function must not throw on a degenerate direct call.
        val count = maxOf(1, columnCount)
        // Running column heights — all columns start empty (height 0).
        val columnHeights = IntArray(count)
        // Visit children strictly in input (child) order — the greedy
        // choice for child i depends on where children 0..i-1 landed.
        return childHeightsPx.map { height ->
            // The column choice rule: smallest running height wins; ties
            // break to the first (lowest) index — identical to the
            // pre-extraction `indices.minByOrNull { columnHeights[it] }`.
            val target = columnHeights.indices.minByOrNull { columnHeights[it] } ?: 0
            // The child's block offset is the column height BEFORE it —
            // children stack flush (no gap), as in the original loop.
            val slot = Slot(target, columnHeights[target])
            // Grow the chosen column by this child's height for the next pick.
            columnHeights[target] += height
            // One slot per child, index-aligned.
            slot
        }
    }

    /**
     * The container's block-size for a distribution: the bottom edge of
     * the lowest-reaching child, i.e. the tallest column. Equivalent to
     * the pre-extraction `columnHeights.maxOrNull() ?: 0` (empty columns
     * contribute 0 and can never exceed an occupied one; no children →
     * 0, same as the original empty-layout answer).
     *
     * @param childHeightsPx the same heights [distribute] consumed.
     * @param slots the assignment [distribute] returned for them.
     */
    fun containerBlockSizePx(childHeightsPx: List<Int>, slots: List<Slot>): Int =
        // max over every child's bottom edge (offset + own height).
        slots.indices.maxOfOrNull { slots[it].yOffsetPx + childHeightsPx[it] } ?: 0
}
