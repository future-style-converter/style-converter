// Pure multi-child block-run fragment geometry for css-break-3 §4 multicol
// fragmentation (wave-42 lane W4) — no Compose imports on purpose: the JUnit
// suite pins this math on the plain JVM and the IDENTICAL R-table is pinned
// by the iOS twin (runtimes/swiftui .../columns/MulticolRunFragment.swift).
package com.styleconverter.runtime.columns

/**
 * The multi-child generalization of the wave-10 single-child slice: the
 * container's in-flow children are ONE continuous block-axis run (each child
 * stacked at the cumulative height of its predecessors, CSS 2.1 §9.4.1
 * block flow), and under `column-fill: auto` with a definite block-size H
 * that run fills column boxes SEQUENTIALLY to H before advancing
 * (css-multicol-1 §7.2) — i.e. the whole run fragments exactly like the
 * sole-child case, so the SAME clip+translate replay draws it: fragment i
 * clips column i and shifts the continuous run paint by (+x_i, −i·H).
 *
 * Deliberately `column-fill: auto` ONLY: under the `balance` initial value
 * Chromium distributes at the best BREAKPOINTS (class A child edges /
 * class B line edges), which the whole-child greedy distribution already
 * approximates on the corpus's green multi-child cells (css-break
 * block-in-inline-009/013/014 at 0.987–0.997, css-multicol baseline-001/002
 * at 0.999) — raw ceil(T/N) slicing would re-introduce exactly the
 * mid-line tearing wave-42 removes elsewhere. Callers gate on the fill
 * mode; this module only owns the geometry.
 */
object MulticolRunFragment {

    /** The run answer: where each child sits in the strip + the replay slices. */
    data class RunPlan(
        /**
         * Block offset of each child in the CONTINUOUS run (column-0 /
         * strip coordinates), index-aligned with the input heights — the
         * place() targets; the replay translates the whole strip per
         * fragment. Usually the flush cumulative stack, except where a
         * MONOLITHIC child was pushed past a column boundary (see the
         * `monolithic` argument of [runPlan]), which leaves a deliberate
         * gap in the strip — the same empty tail Chromium leaves at the
         * bottom of the column the box refused to be split by.
         */
        val yOffsetsPx: List<Int>,
        /** Total run block-size T = Σ max(0, height) — the slice source's C. */
        val totalBlockSizePx: Int,
        /**
         * The css-break-3 §4 fragments over the whole run: empty when the
         * run FITS one column (T ≤ H — content stays in column 0, which is
         * the §7.2 sequential fill; note that differs from the greedy
         * spread, so callers only engage under fill:auto), else the
         * [FragmentGeometry] slices with C = T.
         */
        val fragments: List<FragmentGeometry.Fragment>
    )

    /**
     * Build the run plan for stacked child heights inside a definite-height
     * fill:auto multicol container.
     *
     * @param childHeightsPx measured child block-sizes at column width, in
     *   child order (negative treated as 0 — a box is never negative-tall).
     * @param columnBlockSizePx H — the definite column block-size.
     * @param columnWidthPx W — the §3.4 used column width.
     * @param columnGapPx G — the used column-gap.
     * @param columnCount N — the §3.4 used column count (overflow cap).
     * @param monolithic per-child "this box has NO break point inside it"
     *   flags, index-aligned with [childHeightsPx] (short/absent lists read
     *   as false = breakable, which is the pre-push behaviour). A box with
     *   no children and no text has neither a class-A (between block-level
     *   siblings) nor a class-B (between line boxes) breakpoint anywhere
     *   inside it (css-break-3 §4.1), so a column boundary may not pass
     *   through it: it moves WHOLE to the next column instead.
     */
    fun runPlan(
        childHeightsPx: List<Int>,
        columnBlockSizePx: Int,
        columnWidthPx: Int,
        columnGapPx: Int,
        columnCount: Int,
        monolithic: List<Boolean> = emptyList()
    ): RunPlan {
        // Cumulative stacking: child i starts where child i-1 ended (block
        // flow, margins already inside the measured heights on this path).
        val offsets = ArrayList<Int>(childHeightsPx.size)
        var y = 0
        childHeightsPx.forEachIndexed { index, raw ->
            // Negative measured heights are impossible boxes — floor at 0.
            val h = maxOf(0, raw)
            // ── The monolithic push (css-break-3 §4.1 + §5.2) ──────────
            // A box with no break point inside it cannot be split by a
            // column boundary; the fragmentainer break happens BEFORE it
            // and the whole box starts the next column. Skipped when it
            // already starts flush at a boundary (nothing to push it past
            // — a box taller than H then legitimately breaks, which is the
            // "does not fit anywhere" case css-break-3 §5.2 allows).
            //
            // Measured evidence (WPT css-break, all `column-fill: auto`):
            //  · borders-000 — `[95px spacer][100px bordered box]`, H=100:
            //    pushed, the box lands whole in column 1, which is what the
            //    reference prose asks for ("a yellow box with a hotpink
            //    border in the second column").
            //  · borders-001/002 — same spacer, boxes 120/270 tall: pushed
            //    to column 1 and THEN broken, so the top border is in
            //    column 1 and the bottom border in column 2 / 3 exactly as
            //    those references describe. Without the push both start
            //    5px early, splitting the 10px top border across columns 0
            //    and 1 — a break inside a border the reference forbids.
            //  · avoid-border-break — `[175px spacer][100px green box]`,
            //    H=200: pushed to column 1 where it renders as the required
            //    filled green 100px square; unpushed it would break 25px
            //    into its own 30px border.
            if (columnBlockSizePx > 0 && h > 0 &&
                monolithic.getOrElse(index) { false } &&
                y % columnBlockSizePx != 0
            ) {
                // The column this box starts in vs the one its LAST pixel
                // lands in — different ⇒ a boundary passes through it.
                val startCol = y / columnBlockSizePx
                if (startCol != (y + h - 1) / columnBlockSizePx) {
                    // Break before the box: it opens the next column.
                    y = (startCol + 1) * columnBlockSizePx
                }
            }
            // The child's strip offset BEFORE its own extent.
            offsets.add(y)
            y += h
        }
        // T ≤ H: the run fits column 0 whole — sequential fill, no slices
        // (an empty fragment list = the plain drawContent identity).
        val fragments = if (y > columnBlockSizePx && columnBlockSizePx > 0) {
            // The whole run slices exactly like a sole child of height T —
            // same S-table geometry, same N cap (overflow clip).
            FragmentGeometry.fragmentGeometry(
                childBlockSizePx = y,
                columnBlockSizePx = columnBlockSizePx,
                columnWidthPx = columnWidthPx,
                columnGapPx = columnGapPx,
                columnCount = columnCount
            )
        } else emptyList()
        return RunPlan(offsets, y, fragments)
    }
}
