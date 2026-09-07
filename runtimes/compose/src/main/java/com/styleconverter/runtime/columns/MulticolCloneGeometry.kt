// Pure fragment geometry for css-break-3 §5.4 `box-decoration-break: clone`
// inside a multicol fragmentainer — the CLONE twin of FragmentGeometry's
// slice S-table. No Compose imports on purpose: the JUnit suite pins this
// math on the plain JVM, and the identical K-table is pinned on the iOS
// runtime (MulticolCloneGeometry.swift) so both natives clone byte-
// identically. Wave-46 lane Y3.
package com.styleconverter.runtime.columns

/**
 * Where each fragment of an over-tall `box-decoration-break: clone` child
 * lands, per css-break-3 §5.4: "each box fragment is independently wrapped
 * with the border, padding, and margin … the border-radius … applied to
 * each fragment independently. The background is drawn independently in
 * each fragment."
 *
 * The slice S-table lays the child out ONCE at its full block-size C and
 * scrolls band i into column i (translate −i·H). Clone is different in two
 * ways, and both fall out of ONE measuring trick the Compose and iOS
 * consumers share: the child is laid out at the FRAGMENT's block-size
 * (H for every full fragment) so its own decoration — borders, radius,
 * backgrounds, no-repeat images — paints as one complete box per fragment.
 *
 *  1. Capacity: every fragment spends `blockStartPx + blockEndPx` of its H
 *     on its OWN decoration bands (border + padding, §5.2 "independently
 *     wrapped"), so the content advances by `H − bands` per fragment, not
 *     by H. WPT css-break borders-008: 240px of content, H = 100, 10px
 *     borders → 80px per fragment → 3 fragments, each a full 100px circle.
 *  2. Replay: fragment i shows the H-tall clone paint with NO block
 *     translate (translateY = 0 — the same box, again). A SHORT last
 *     fragment (leftover content + bands < H) is composed from two bands
 *     of that same H-tall paint: its top rows verbatim, and its block-end
 *     decoration band taken from the BOTTOM of the paint (translate
 *     `boxH − H`), so the bottom border / bottom corners land at the
 *     fragment's true block-end edge. (iOS re-renders the child at the
 *     short size instead and gets an exact last fragment; the band split
 *     is the Compose single-measure approximation, documented in
 *     MulticolCloneMeasure.)
 *
 * Horizontal-tb only, like the slice table: vertical writing modes bail
 * (blocked-platform) before reaching this function.
 */
object MulticolCloneGeometry {

    /**
     * The child's own block-axis decoration bands, in px — what a clone
     * fragment wraps its content slice with (css-break-3 §5.4).
     */
    data class Bands(
        /** border-top + padding-top (block-start band under horizontal-tb). */
        val blockStartPx: Int,
        /** border-bottom + padding-bottom (block-end band). */
        val blockEndPx: Int,
        /**
         * The rows of the H-tall clone paint that must be copied to a
         * SHORT last fragment's block-end edge: `max(blockEndPx, the
         * largest bottom corner's vertical radius)` — a bottom corner arc
         * extends above the border band, so the band must be at least as
         * tall as the arc for the corners to rejoin (borders-008's circles).
         */
        val blockEndBandPx: Int
    )

    /**
     * Clone fragment list for a child whose UNFRAGMENTED border-box
     * block-size is [childBlockSizePx] (C, the intrinsic probe's answer —
     * content + both bands) inside a fragmentainer of block-size
     * [columnBlockSizePx] (H), used column width [columnWidthPx] (W), gap
     * [columnGapPx] (G) and used count [columnCount] (N).
     *
     * Returns null when NO content fits a fragment (H ≤ bands): the clone
     * model has no answer there — callers fall back to slice and log.
     * Fragment count F = ceil(content / (H − bands)), at least 1, capped at
     * N exactly like the slice table (the column-fill:auto overflow cap —
     * the tail simply never gets a column). Entries are in DRAW order:
     * full fragments contribute one entry; a short last fragment two (its
     * top rows, then its block-end band).
     */
    fun cloneFragments(
        childBlockSizePx: Int,
        columnBlockSizePx: Int,
        bands: Bands,
        columnWidthPx: Int,
        columnGapPx: Int,
        columnCount: Int
    ): List<FragmentGeometry.Fragment>? {
        // Defensive floors mirroring FragmentGeometry: count is a positive
        // <integer> (css-multicol §3.2), gaps non-negative (css-align §8),
        // a negative width clips to nothing, bands cannot be negative
        // (CSS 2.1 §8.4/§8.5 — padding and border widths are ≥ 0).
        val count = maxOf(1, columnCount)
        val gap = maxOf(0, columnGapPx)
        val width = maxOf(0, columnWidthPx)
        val startBand = maxOf(0, bands.blockStartPx)
        val endBand = maxOf(0, bands.blockEndPx)
        val h = columnBlockSizePx
        // Per-fragment CONTENT capacity: the fragmentainer minus the bands
        // every clone fragment re-spends on its own decoration (§5.2).
        val capacity = h - startBand - endBand
        // No fragmentainer, or bands alone fill it: clone is undefined
        // (every fragment would be pure decoration, content never
        // advances) — null, the caller's slice fallback + log.
        if (h <= 0 || capacity <= 0) return null
        // The content block-size: the unfragmented box minus ONE pair of
        // bands (the unfragmented layout wraps the content exactly once).
        val content = maxOf(0, childBlockSizePx - startBand - endBand)
        // ceil(content / capacity) in Long space so a pathological child
        // near Int.MAX_VALUE cannot wrap the ceiling addition negative.
        val rawCount = (content.toLong() + capacity - 1) / capacity
        // At least one fragment (an empty clone box still owns column 0),
        // at most N — the slice table's identical overflow cap.
        val fragmentCount = rawCount.coerceIn(1L, count.toLong()).toInt()
        // Build in draw order; a short last fragment appends two entries.
        val out = ArrayList<FragmentGeometry.Fragment>(fragmentCount + 1)
        for (i in 0 until fragmentCount) {
            // Inline origin of column i — exactly i gaps precede column i.
            val x = i * (width + gap)
            // Content left for THIS fragment; every fragment before the last
            // spends a full capacity, the last takes the remainder. When the
            // N cap truncated the list the remainder exceeds the capacity
            // and the fragment is a full H box (the overflow is clipped).
            val remaining = content - i.toLong() * capacity
            // The fragment's border-box block-size: remainder + its own
            // bands, never more than the fragmentainer.
            val boxH = minOf(h.toLong(), remaining + startBand + endBand).toInt()
            if (boxH >= h) {
                // A full fragment: the whole H-tall clone paint, untranslated
                // in the block axis (the same complete box, again).
                out.add(FragmentGeometry.Fragment(i, x, 0, width, h, x, 0))
            } else {
                // A SHORT last fragment. Its block-end band (border +
                // padding, widened to the bottom corner radius) comes from
                // the BOTTOM of the H-tall paint so the bottom border and
                // corner arcs sit at the fragment's real block-end edge;
                // the rows above it are the paint's own top rows.
                val band = bands.blockEndBandPx.coerceIn(0, boxH)
                val topRows = boxH - band
                // Top rows: clip [0, topRows), no translate.
                if (topRows > 0) {
                    out.add(FragmentGeometry.Fragment(i, x, 0, width, topRows, x, 0))
                }
                // Block-end band: clip [topRows, boxH), paint shifted UP by
                // (H − boxH) so the paint's bottom `band` rows land there.
                if (band > 0) {
                    out.add(FragmentGeometry.Fragment(i, x, topRows, width, band, x, boxH - h))
                }
            }
        }
        return out
    }
}
