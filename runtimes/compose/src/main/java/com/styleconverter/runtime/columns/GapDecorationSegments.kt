package com.styleconverter.runtime.columns

// css-gap-decorations-1 §3-§5 — the flex gap-decoration SEGMENT MODEL
// (wave 24, lane GAPS-A). Pure geometry: rectangles in, rectangles out.
//
// Every number in the comments below was re-derived from the WPT
// REFERENCE documents for css-gaps/flex (fixtures/wpt/css-gaps/
// flex__flex-gap-decorations-NNN__ref.json — the refs are the browser's
// own answer expressed as absolutely-positioned boxes, so they pin the
// geometry exactly). The shared 003-family layout used throughout is:
// 2px border, 170px content box at origin (2,2), column-gap 10, row-gap
// 10, wrap, item widths 50/50/50/100/50/50/50/50 × 50px tall, giving
//   line 1 items x [2,52] [62,112] [122,172], cross [2,52]
//   line 2 items x [2,102] [112,162],         cross [62,112]
//   line 3 items x [2,52] [62,112] [122,172], cross [122,172]
//
// THE MODEL, and what pins each rule:
//  (b) a within-line (column) rule is one segment per adjacent item pair,
//      of the declared width, CENTERED in the item-to-item gap, spanning
//      that LINE's cross extent — pinned by 007 (line extent, not item
//      extent), 008 (five segments, painted outside the container box),
//      012 (`column-over-row` makes the extent observable: the 2px red
//      rules are EXACTLY [2,52] and [122,172], so the segment does NOT
//      run into the row gaps).
//  (c) a between-line (row) rule spans the container CONTENT BOX along
//      the main axis, centered in the inter-line gap — 003/010/011/012
//      all draw it as a full 170px bar at content-box left.
//  (d) `intersection` cuts a rule at every crossing GAP (not at the
//      crossing rule's width) — 034 is decisive: 5px rules, 90px gaps,
//      and the ref cuts the row rule by the full 90px columns.
//  (e) `*-rule-inset` shortens both ends, negative lengthens — 013 (+2px)
//      and 011 (-2px, which escapes the content box).
//  (f) `rule-overlap` is paint ORDER only — 012.

/**
 * Builds the ordered list of rule rectangles for one flex container.
 *
 * Inputs are all in the container's CONTENT-BOX coordinate space, real
 * pixels. The caller (the draw hook) is responsible for translating the
 * placed item bounds into that space; this object does no unit work.
 */
object GapDecorationSegments {

    /**
     * @param config        the container's resolved gap-decoration config.
     * @param items         placed flex-item rectangles, in placement order.
     * @param contentBox    the container's content box, origin-relative
     *                      (normally (0,0,w,h)); supplies the span of the
     *                      between-line rules.
     * @param mainHorizontal true for `flex-direction: row|row-reverse` in
     *                      a horizontal writing mode. Determines which
     *                      physical family each gap belongs to: with a
     *                      horizontal main axis the item-to-item gaps are
     *                      horizontal → COLUMN rules, and the inter-line
     *                      gaps are vertical → ROW rules; with a vertical
     *                      main axis the two swap.
     * @return segments in PAINT ORDER (first painted first).
     */
    fun build(
        config: GapDecorationConfig,
        items: List<GapRect>,
        contentBox: GapRect,
        mainHorizontal: Boolean
    ): List<GapSegment> {
        // Inert configs short-circuit: this is the gate that keeps every
        // existing fixture byte-identical (nothing declares these props).
        if (!config.active || items.isEmpty()) return emptyList()

        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items, mainHorizontal), mainHorizontal
        )
        // Which physical family owns which gap kind (see @param above).
        val withinAxis = if (mainHorizontal) GapAxis.COLUMN else GapAxis.ROW
        val betweenAxis = if (mainHorizontal) GapAxis.ROW else GapAxis.COLUMN
        val interLine = GapDecorationLines.betweenLineGaps(lines)

        val within = withinLineSegments(
            config.specFor(withinAxis), withinAxis, lines, interLine, mainHorizontal
        )
        val between = betweenLineSegments(
            config.specFor(betweenAxis), betweenAxis, lines, contentBox, mainHorizontal
        )

        // (f) rule-overlap decides which family lands on top; painting is
        // last-wins, so the loser goes first. Initial value is
        // ROW_OVER_COLUMN, so columns paint first (WPT 012 flips it).
        val columns = if (withinAxis == GapAxis.COLUMN) within else between
        val rows = if (withinAxis == GapAxis.COLUMN) between else within
        return when (config.overlap) {
            GapRuleOverlap.ROW_OVER_COLUMN -> columns + rows
            GapRuleOverlap.COLUMN_OVER_ROW -> rows + columns
        }
    }

    /**
     * (b) One segment per adjacent item pair per line.
     *
     * [interLineGaps] are handed in as potential cuts so `intersection` is
     * implemented symmetrically for both families. In flex they can never
     * actually cut anything — a line's cross extent lies strictly between
     * its neighbouring inter-line gaps — so this is a PROVEN no-op here,
     * asserted by a unit test rather than assumed. Grid/multicol would use
     * the same operator with real cuts.
     */
    private fun withinLineSegments(
        spec: GapRuleSpec,
        axis: GapAxis,
        lines: List<GapFlexLine>,
        interLineGaps: List<GapInterval>,
        mainHorizontal: Boolean
    ): List<GapSegment> {
        if (!spec.paints) return emptyList()
        val cuts = if (spec.breakMode == GapRuleBreak.INTERSECTION) interLineGaps else emptyList()
        val out = mutableListOf<GapSegment>()
        for (line in lines) {
            for (gap in line.mainGaps) {
                // Declared width, centered in the item-to-item gap.
                val band = GapIntervals.band(gap, spec.effectiveWidthPx)
                // Length runs along the CROSS axis, over the line's extent.
                for (piece in GapIntervals.subtract(line.cross, cuts)) {
                    val len = GapIntervals.inset(piece, spec.insetPx) ?: continue
                    // Band is main-axis, length is cross-axis: assemble in
                    // physical (x, y) order according to the main axis.
                    val rect = if (mainHorizontal) GapIntervals.rect(band, len)
                    else GapIntervals.rect(len, band)
                    out.add(GapSegment(rect, axis))
                }
            }
        }
        return out
    }

    /**
     * (c)+(d) One rule per adjacent line pair, spanning the content box
     * along the main axis and cut — under `intersection` — by the union of
     * BOTH adjacent lines' item-to-item gaps.
     *
     * The two-line union is the part that is easy to get wrong and is
     * pinned three times: WPT 009 (cuts {[52,62],[112,122]} ∪ {[102,112]}
     * → segments [2,52] [62,102] [122,172]), WPT 034 (four segments on a
     * 600px box), WPT 050 (six segments with `justify-content:
     * space-between` opening unequal gaps on the two lines).
     */
    private fun betweenLineSegments(
        spec: GapRuleSpec,
        axis: GapAxis,
        lines: List<GapFlexLine>,
        contentBox: GapRect,
        mainHorizontal: Boolean
    ): List<GapSegment> {
        if (!spec.paints || lines.size < 2) return emptyList()
        // The span every between-line rule is drawn across.
        val span = GapDecorationLines.mainOf(contentBox, mainHorizontal)
        val out = mutableListOf<GapSegment>()
        for (i in 0 until lines.size - 1) {
            val gap = GapInterval(lines[i].cross.end, lines[i + 1].cross.start)
            if (gap.isEmpty) continue
            val band = GapIntervals.band(gap, spec.effectiveWidthPx)
            val cuts = if (spec.breakMode == GapRuleBreak.INTERSECTION) {
                lines[i].mainGaps + lines[i + 1].mainGaps
            } else {
                emptyList()
            }
            for (piece in GapIntervals.subtract(span, cuts)) {
                // The inset applies to EACH surviving piece's two ends —
                // WPT 009/034 set it to 0 so the pieces end exactly on the
                // cut edges, which is what that convention reproduces.
                val len = GapIntervals.inset(piece, spec.insetPx) ?: continue
                // Band is cross-axis here, length is main-axis.
                val rect = if (mainHorizontal) GapIntervals.rect(len, band)
                else GapIntervals.rect(band, len)
                out.add(GapSegment(rect, axis))
            }
        }
        return out
    }
}
