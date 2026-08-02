package com.styleconverter.runtime.columns

// css-gap-decorations-1 / css-flexbox-1 §9.3 — recovering FLEX LINES from
// placed item rectangles (wave 24, lane GAPS-A).
//
// The painter runs after layout, so it never re-runs the flex algorithm:
// it is handed the item rectangles the Compose flex container actually
// placed and has to rebuild the line structure from them, because every
// gap decoration is scoped to a line:
//   * a column rule lives in ONE line's item-to-item gap and spans THAT
//     LINE's cross extent (not the item's — WPT flex-gap-decorations-007
//     pins this: with `align-items: flex-end` and one 40px-tall item, the
//     rules are 40px tall and start at the line's top, even though their
//     immediate neighbours are ~19px text boxes sitting at the bottom);
//   * a row rule lives BETWEEN two lines and is cut by the column gaps of
//     both of them (WPT 009 / 034 / 050).

/**
 * One reconstructed flex line: the items on it, its cross extent, and the
 * main-axis gaps between its adjacent items.
 *
 * @param items      the line's item rectangles in main-axis order.
 * @param cross      union of the items' cross extents — the span every
 *                   within-line rule on this line is drawn to.
 * @param mainGaps   the empty main-axis intervals between adjacent items
 *                   (margin box to margin box), already filtered of
 *                   degenerate/zero-size entries.
 */
data class GapFlexLine(
    val items: List<GapRect>,
    val cross: GapInterval,
    val mainGaps: List<GapInterval>
)

/**
 * Line reconstruction. Pure and Compose-free so the tables below can be
 * pinned on the JVM.
 */
object GapDecorationLines {

    /** The rect's extent along the MAIN axis. */
    fun mainOf(r: GapRect, mainHorizontal: Boolean): GapInterval =
        if (mainHorizontal) GapInterval(r.left, r.right) else GapInterval(r.top, r.bottom)

    /** The rect's extent along the CROSS axis. */
    fun crossOf(r: GapRect, mainHorizontal: Boolean): GapInterval =
        if (mainHorizontal) GapInterval(r.top, r.bottom) else GapInterval(r.left, r.right)

    /**
     * Group placed items into lines.
     *
     * Two independent signals, both required, because neither alone is
     * safe for every fixture in the pinned set:
     *
     *  1. CROSS-AXIS DISJOINTNESS — a new line starts when the item's
     *     cross extent no longer overlaps the accumulated cross extent of
     *     the line being built. This is the signal the campaign brief
     *     names, and it is what separates WPT 003's three lines
     *     ([2,52] / [62,112] / [122,172]).
     *  2. MAIN-AXIS BACKTRACK — a new line also starts when an item's main
     *     start is BEFORE the previous item's main start. Needed because
     *     signal 1 alone would merge two wrapped lines whose items happen
     *     to overlap in cross position (`align-content` packing with a
     *     negative or zero row gap), and it is the classic wrap detector.
     *
     * Items are consumed in the order the layout placed them, which for a
     * flex container is already main-axis order within each line
     * (css-flexbox-1 §9.3 collects items in order). No sorting: sorting
     * would destroy the backtrack signal.
     */
    fun groupIntoLines(items: List<GapRect>, mainHorizontal: Boolean): List<List<GapRect>> {
        if (items.isEmpty()) return emptyList()
        val lines = mutableListOf<MutableList<GapRect>>()
        var current = mutableListOf<GapRect>()
        // Running cross extent of the line under construction.
        var crossStart = 0f
        var crossEnd = 0f
        for (item in items) {
            val main = mainOf(item, mainHorizontal)
            val cross = crossOf(item, mainHorizontal)
            if (current.isEmpty()) {
                current.add(item); crossStart = cross.start; crossEnd = cross.end; continue
            }
            val prevMain = mainOf(current.last(), mainHorizontal)
            // Signal 2 first (cheaper, and strictly stronger when it fires).
            val backtracked = main.start < prevMain.start
            // Signal 1: no overlap at all with the line's cross band.
            val disjoint = cross.start >= crossEnd || cross.end <= crossStart
            if (backtracked || disjoint) {
                lines.add(current)
                current = mutableListOf(item)
                crossStart = cross.start; crossEnd = cross.end
            } else {
                current.add(item)
                crossStart = minOf(crossStart, cross.start)
                crossEnd = maxOf(crossEnd, cross.end)
            }
        }
        lines.add(current)
        return lines
    }

    /**
     * Promote grouped rectangles to [GapFlexLine]s: cross extent as the
     * union of the members, main gaps as the gaps between main-adjacent
     * members.
     *
     * The gap is measured MARGIN BOX to MARGIN BOX per
     * css-gap-decorations-1 §3. Compose has no margin concept — an item's
     * placed bounds ARE its margin box whenever margins are zero, which is
     * true for every pinned WPT fixture in this section (none of
     * 001-014 / 022 / 034 / 050 declares a margin on a flex item). Non-zero
     * item margins are therefore a known, stated gap in this lane rather
     * than a silent approximation.
     */
    fun toLines(grouped: List<List<GapRect>>, mainHorizontal: Boolean): List<GapFlexLine> =
        grouped.filter { it.isNotEmpty() }.map { line ->
            // Cross extent: union over the line's members.
            val crossIvs = line.map { crossOf(it, mainHorizontal) }
            val cross = GapInterval(crossIvs.minOf { it.start }, crossIvs.maxOf { it.end })
            // Main gaps: sort by main start so a line whose members arrive
            // out of main order still yields left-to-right gaps.
            //
            // LIMITATION (skeptic wave-24, executed pin in
            // SkepticGapDecorationProbeTest): this sort does NOT rescue a
            // fully reversed report order. [groupIntoLines]'s backtrack
            // signal fires first and splits every item of a reversed line
            // into its own line, so nothing reaches here to be sorted. The
            // draw hook reports item rects in DOCUMENT order, which equals
            // placement order only while the renderer ignores
            // FlexDecision.reverse (it does today — `reverse` is carried on
            // the decision but never applied). If row-reverse/column-reverse
            // placement ever lands, groupIntoLines must be taught to accept
            // a reversed run BEFORE this sort can matter.
            val ordered = line.sortedBy { mainOf(it, mainHorizontal).start }
            val gaps = mutableListOf<GapInterval>()
            for (i in 0 until ordered.size - 1) {
                val a = mainOf(ordered[i], mainHorizontal)
                val b = mainOf(ordered[i + 1], mainHorizontal)
                // Overlapping or touching neighbours open no gap → no rule.
                val gap = GapInterval(a.end, b.start)
                if (!gap.isEmpty) gaps.add(gap)
            }
            GapFlexLine(items = ordered, cross = cross, mainGaps = gaps)
        }.sortedBy { it.cross.start }
    // Lines are returned in CROSS order, not placement order, so that
    // "adjacent lines" always means cross-adjacent. `flex-wrap:
    // wrap-reverse` (and `align-content` orderings) place the first line
    // last on screen; without this sort betweenLineGaps would compute a
    // negative gap between line 0 and line 1 and drop every row rule.

    /**
     * The empty cross-axis intervals BETWEEN consecutive lines — where the
     * between-line rules are drawn, and (under `intersection`) what would
     * cut a within-line rule.
     */
    fun betweenLineGaps(lines: List<GapFlexLine>): List<GapInterval> {
        val gaps = mutableListOf<GapInterval>()
        for (i in 0 until lines.size - 1) {
            val gap = GapInterval(lines[i].cross.end, lines[i + 1].cross.start)
            // A zero/negative inter-line gap paints no rule (and cuts nothing).
            if (!gap.isEmpty) gaps.add(gap)
        }
        return gaps
    }
}
