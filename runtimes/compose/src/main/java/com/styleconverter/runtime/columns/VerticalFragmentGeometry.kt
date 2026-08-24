// Wave-47 lane Z2 — pure fragment geometry for css-break-3 §4 multicol
// fragmentation under VERTICAL writing modes. No Compose imports on purpose:
// the JUnit suite pins this math on the plain JVM, and the identical V-table
// is pinned on the iOS runtime (VerticalFragmentGeometry.swift) so both
// platforms fragment byte-identically — the same cross-platform contract
// FragmentGeometry.kt documents for horizontal-tb.
package com.styleconverter.runtime.columns

/**
 * Fragment list for a vertical-writing-mode multicol container's sole
 * over-wide child, per css-multicol-1 §2 + css-break-3 §4 with the
 * `box-decoration-break: slice` default of §6:
 *
 * Axes transpose against [FragmentGeometry]: the container's INLINE axis is
 * vertical, so the N column boxes STACK VERTICALLY, each `colH` tall
 * (`colH = (availableInline − (N−1)·G) / N`) and W wide, where W — the
 * container's content-box WIDTH — is the fragmentainer BLOCK size the child
 * breaks at. The child lays out ONCE as a continuous box of block extent C
 * (its used physical WIDTH) × colH; fragment i then shows one W-wide band of
 * that continuous paint inside column i — a pure clip + translate, consumed
 * by the same drawWithContent replay as the horizontal pass.
 *
 * The BLOCK-FLOW DIRECTION decides which physical band fragment i shows and
 * where a partial band sits (css-writing-modes-4 §6.4):
 *  * vertical-lr — block flows left→right: fragment i is the child's
 *    physical x-band [i·W, (i+1)·W), left-aligned in its column
 *    (translateX = −i·W); the last partial band leaves the column's RIGHT
 *    remainder showing the container's own paint.
 *  * vertical-rl — block flows right→left: fragment i is the band
 *    [C−(i+1)·W, C−i·W), RIGHT-aligned in its column. One formula covers
 *    full and partial bands: translateX = W − C + i·W (band start lands at
 *    max(0, W − remaining), pinned by the V-table test against the
 *    css-break background-image-001 / borders-007 reference geometry).
 */
object VerticalFragmentGeometry {

    /**
     * Build the fragment list.
     *
     * @param childBlockSizePx  C — the child's laid-out physical width.
     * @param columnBlockSizePx W — the container's content width (the
     *        fragmentainer block size each fragment breaks at).
     * @param columnInlineSizePx colH — one column box's height
     *        ((availableInline − gaps) / N, resolved by the caller).
     * @param columnGapPx       G — the used column-gap (between columns,
     *        i.e. VERTICALLY here).
     * @param columnCount       N — the used column count (caps F, the
     *        column-fill:auto overflow clip — same rule as horizontal).
     * @param blockRtl          true for vertical-rl / sideways-rl (block
     *        axis right→left), false for vertical-lr (left→right).
     */
    fun fragmentGeometry(
        childBlockSizePx: Int,
        columnBlockSizePx: Int,
        columnInlineSizePx: Int,
        columnGapPx: Int,
        columnCount: Int,
        blockRtl: Boolean,
    ): List<FragmentGeometry.Fragment> {
        // Defensive floors — the same contracts as FragmentGeometry: used
        // count is a positive <integer> (css-multicol §3.2), gaps are
        // non-negative (css-align §8), negative extents clip to nothing.
        val count = maxOf(1, columnCount)
        val gap = maxOf(0, columnGapPx)
        val colH = maxOf(0, columnInlineSizePx)
        val w = columnBlockSizePx
        // No fragmentainer extent → the degenerate single fragment (empty
        // clip), mirroring the horizontal guard: callers gate on C > W > 0
        // and never reach this branch in practice.
        if (w <= 0) {
            return listOf(FragmentGeometry.Fragment(0, 0, 0, maxOf(0, w), colH, 0, 0))
        }
        val c = maxOf(0, childBlockSizePx)
        // F = ceil(C / W) in Long space (overflow-safe), at least 1, capped
        // at N — the paint past column N−1 simply never gets a column, which
        // IS the column-fill:auto overflow clip (same rule as horizontal).
        val rawCount = (c.toLong() + w - 1) / w
        val fragmentCount = rawCount.coerceIn(1L, count.toLong()).toInt()
        return List(fragmentCount) { i ->
            // Column i's vertical origin — columns stack downward with
            // exactly i gaps above column i (css-multicol §3 box placement,
            // transposed to the vertical inline axis).
            val y = i * (colH + gap)
            // Block-band translation (doc comment above): left-aligned
            // −i·W bands for lr; right-aligned W−C+i·W bands for rl.
            val tx = if (blockRtl) w - c + i * w else -i * w
            // Clip = column i's rect in container coordinates (full content
            // width × colH); translate moves band i of the continuous child
            // paint into it (+y vertically — the child is laid out at the
            // container origin, colH tall).
            FragmentGeometry.Fragment(
                columnIndex = i,
                clipLeft = 0,
                clipTop = y,
                clipWidth = w,
                clipHeight = colH,
                translateX = tx,
                translateY = y,
            )
        }
    }

    /**
     * The used column inline size colH = (availableInline − (N−1)·G) / N,
     * floored at 0 — the css-multicol §3.4 width fit transposed to the
     * vertical inline axis. Shared by the measure helper and the V-table
     * test so the geometry and the gate can never disagree.
     */
    fun columnInlineSizePx(availableInlinePx: Int, columnCount: Int, columnGapPx: Int): Int {
        val count = maxOf(1, columnCount)
        val gap = maxOf(0, columnGapPx)
        return maxOf(0, (availableInlinePx - (count - 1) * gap) / count)
    }
}
