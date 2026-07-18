// Pure fragment geometry for css-break-3 §4 multicol fragmentation — no Compose
// imports on purpose: the JUnit suite pins this math on the plain JVM, and the
// identical S-table is pinned on the iOS runtime (MulticolMath) so both
// platforms fragment byte-identically.
package com.styleconverter.runtime.columns

/**
 * Computes where each fragment of an over-tall multicol child lands, per
 * css-break-3 §4 (fragmentation into column boxes) with the
 * `box-decoration-break: slice` default of css-break-3 §6:
 *
 * - the child is laid out ONCE at column width as a single continuous box of
 *   block-size C (backgrounds, tiles, borders painted as if unfragmented);
 * - fragment i (0-based) then shows the [i*H, min((i+1)*H, C)) band of that
 *   continuous paint, moved into column i — a pure clip + translate, which is
 *   exactly how the Compose draw pass consumes this list.
 *
 * Horizontal-tb only: callers bail (and log once) before reaching this
 * function when writing-mode is vertical — vertical fragmentation is
 * classified blocked-platform and deliberately not modeled here.
 */
object FragmentGeometry {

    /**
     * One fragment of the sliced child: which column it occupies, the clip
     * rect bounding it (container coordinates, x growing inline / y growing
     * block), and the translation applied to the child's continuous paint so
     * the right band shows through the clip.
     */
    data class Fragment(
        /** 0-based column the fragment renders in (fragment i → column i, css-break-3 §4.1). */
        val columnIndex: Int,
        /** Clip rect left edge: the column's inline origin x_i = i * (W + G). */
        val clipLeft: Int,
        /** Clip rect top edge: every column box starts at block offset 0. */
        val clipTop: Int,
        /** Clip rect inline size: the used column width W. */
        val clipWidth: Int,
        /** Clip rect block size: the column block-size H (caps each fragment's band). */
        val clipHeight: Int,
        /** Inline translation of the continuous child paint: +x_i, into column i. */
        val translateX: Int,
        /** Block translation: −i*H, scrolling band i of the continuous paint into view. */
        val translateY: Int
    )

    /**
     * Fragment list for a child of laid-out block-size [childBlockSizePx] (C)
     * inside a multicol container with column block-size [columnBlockSizePx]
     * (H), used column width [columnWidthPx] (W), used gap [columnGapPx] (G)
     * and used column count [columnCount] (N).
     *
     * Fragment count F = ceil(C / H), capped at N with the paint beyond
     * column N-1 clipped away — matching Chromium's column-fill:auto overflow
     * behavior for this fixture family (the tail simply never gets a column,
     * so drawing only F ≤ N fragments IS the clip). C ≤ H degenerates to a
     * single identity fragment (columnIndex 0, zero translate), i.e. the
     * unfragmented rendering — callers keep their pre-existing layout path
     * for that case and never need this list.
     */
    fun fragmentGeometry(
        childBlockSizePx: Int,
        columnBlockSizePx: Int,
        columnWidthPx: Int,
        columnGapPx: Int,
        columnCount: Int
    ): List<Fragment> {
        // Defensive floors mirroring MultiColumnApplier.resolveUsedColumns: the
        // used count is a positive <integer> (css-multicol §3.2), gaps are
        // non-negative (css-align §8), and a negative width clips to nothing.
        val count = maxOf(1, columnCount)
        val gap = maxOf(0, columnGapPx)
        val width = maxOf(0, columnWidthPx)
        // A non-positive column block-size means there is no fragmentainer to
        // slice into — emit the single degenerate fragment (empty clip) rather
        // than divide by zero below; the caller's overflow gate (C > H > 0)
        // never reaches this branch, it exists purely as a guard.
        if (columnBlockSizePx <= 0) {
            return listOf(Fragment(0, 0, 0, width, maxOf(0, columnBlockSizePx), 0, 0))
        }
        // ceil(C / H) in Long space so a pathological child height near
        // Int.MAX_VALUE cannot wrap the ceiling addition negative.
        val rawCount = (maxOf(0, childBlockSizePx).toLong() + columnBlockSizePx - 1) / columnBlockSizePx
        // At least one fragment always exists (even a zero-height child owns
        // column 0), and at most N — the column-fill:auto overflow cap.
        val fragmentCount = rawCount.coerceIn(1L, count.toLong()).toInt()
        // Build fragment i: column i's inline origin is i*(W+G) (css-multicol
        // §3 column box placement); the clip is that column's box; the paint
        // translation moves band [i*H, (i+1)*H) of the continuous child up
        // into the clip (−i*H in block) and across into the column (+x_i).
        return List(fragmentCount) { i ->
            // Inline origin of column i — gaps sit BETWEEN columns, so exactly
            // i gaps precede column i.
            val x = i * (width + gap)
            // Clip = column i's rect; translate = (+x_i, −i*H) per the shared
            // cross-platform S-table (S1..S5).
            Fragment(i, x, 0, width, columnBlockSizePx, x, -i * columnBlockSizePx)
        }
    }
}
