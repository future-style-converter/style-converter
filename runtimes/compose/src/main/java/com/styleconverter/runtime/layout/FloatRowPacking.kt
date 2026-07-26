package com.styleconverter.runtime.layout

// FloatRowPacking — the PURE half of CSS 2.1 §9.5 float row packing
// (wave-19 lane FLOAT). Byte-parallel twin of iOS
// StyleEngine/layout/FloatRowPacking.swift — same names, same pinned
// semantics (P1-P6 in the lane pin table); skeptic probes diff the two.
//
// Scope (narrow, corpus-sufficient — see the lane contract):
//  • F1: consecutive same-direction LEFT floats pack into a horizontal
//    run, wrapping at width exhaustion (§9.5.1 rules 1-3,7 — greedy,
//    no interleaving with in-flow line boxes).
//  • F2: a childless clear sibling (the `<br clear>` wire shape) breaks
//    the run and marks it STRUTTED (§9.5.2 clearance + the br's empty
//    line box in the composed-WPT ref).
//  • F4: right/inline-end floats and non-float siblings keep today's
//    behaviour — they are never absorbed into a run (P1).
// The composable adapter lives in FloatRowLayout.kt; this file has no
// Compose dependency so the JVM pinning suite runs it directly.
object FloatRowPacking {

    // P6 — the composed-WPT ref line-box pin, in CSS px: 16px ref font ×
    // REF_DEFAULT_FONT_LINE_HEIGHT_RATIO 1.25 (WptCaptureMode.kt) == iOS
    // ComponentRenderer.wptRefLineBoxPx 20. A `<br clear:both>` occupies
    // one empty line box in the browser-ref, so the next float row
    // starts below max(float margin-bottom edge, line-box bottom).
    const val STRUT_PX: Double = 20.0

    /**
     * Per-sibling facts the segmenter consumes — derived ONCE per child
     * from its IR Float/Clear keywords (FloatExtractor is the single
     * owner of float/clear keyword parsing on this platform).
     */
    data class ChildFacts(
        // P1: Float ∈ {LEFT, INLINE_START} — the LTR-normalized engine
        // treats inline-start as left (css-logical-1 §2.1).
        val floatsLeft: Boolean,
        // P2: childless + Float=NONE + Clear ∈ {BOTH, LEFT, INLINE_START}
        // — the `<br clear>` IR shape. Clear RIGHT/INLINE_END does NOT
        // clear a left run (§9.5.2: clearance only past same-side floats).
        val clearBreaksLeft: Boolean,
    )

    /**
     * Derive [ChildFacts] from an extracted [FloatConfig]. Kept pure over
     * the typed config (not raw IR) so both call sites — the renderer and
     * the JVM tests — share one derivation.
     * [hasChildren] guards the clear-break shape: a clear on a CONTENT
     * box is real layout (out of the narrow contract), only the childless
     * `<br>` marker is a pure row break.
     */
    fun facts(config: FloatConfig, hasChildren: Boolean): ChildFacts = ChildFacts(
        // P1 — left-family floats only; RIGHT/INLINE_END keep the wave-5
        // end-alignment path (F4), NONE is plain in-flow content.
        floatsLeft = config.float == FloatValue.LEFT ||
            config.float == FloatValue.INLINE_START,
        // P2 — the break marker must not itself float and must be
        // childless (the wire's `<br clear>` has neither).
        clearBreaksLeft = !hasChildren && config.float == FloatValue.NONE &&
            (config.clear == ClearValue.BOTH || config.clear == ClearValue.LEFT ||
                config.clear == ClearValue.INLINE_START),
    )

    /**
     * One segment of the child list, in sibling order: either a float
     * RUN (≥2 consecutive left-floats, rendered side-by-side by the
     * adapter) or a SINGLE child that keeps the block-flow path.
     */
    data class Segment(
        // Child indices (into the original sibling list) in order.
        val indices: List<Int>,
        // True for a packable float run (always ≥2 indices then).
        val isRun: Boolean,
        // P2/P6: the sibling immediately after the run is a clear-break
        // marker — the adapter applies the line-box strut to this run.
        val strutted: Boolean,
    )

    /**
     * P1 — split the sibling list into maximal float runs and singles.
     * A run is ≥2 CONSECUTIVE left-floating siblings; anything else
     * (clear marker, right float, in-flow box) ends the streak and
     * renders as a single. Lone left-floats stay singles too — today's
     * path already renders one float correctly, and keeping it identical
     * minimizes the gated surface (F4 conservatism).
     */
    fun segment(children: List<ChildFacts>): List<Segment> {
        // Output accumulator — segments in sibling order.
        val out = mutableListOf<Segment>()
        // Current streak of consecutive left-float indices.
        val streak = mutableListOf<Int>()
        // Flush the open streak: a run when ≥2, singles otherwise.
        // [next] is the index AFTER the streak (or null at the end) —
        // it decides the strut (P2: a trailing clear-break marker).
        fun flush(next: Int?) {
            if (streak.size >= 2) {
                // The strut fires only when the run is terminated by a
                // clear-break sibling (the `<br clear>` line box, P6).
                val strut = next != null && children[next].clearBreaksLeft
                out.add(Segment(streak.toList(), isRun = true, strutted = strut))
            } else {
                // 0 or 1 floats — each keeps the plain block path.
                streak.forEach { out.add(Segment(listOf(it), isRun = false, strutted = false)) }
            }
            streak.clear()
        }
        // Single pass over the siblings, building streaks.
        children.forEachIndexed { i, c ->
            if (c.floatsLeft) {
                // Extend the current left-float streak.
                streak.add(i)
            } else {
                // Streak broken by this sibling — flush, then emit the
                // breaker itself as a single (clear markers render ~0-size).
                flush(i)
                out.add(Segment(listOf(i), isRun = false, strutted = false))
            }
        }
        // Trailing streak: no next sibling → never strutted.
        flush(null)
        return out
    }

    /** Resolved geometry for one run: per-child origins + reported size. */
    data class RunLayout(
        // Per-child x origin (margin-box left edge), run-relative px.
        val x: List<Double>,
        // Per-child y origin (top of the child's float row), px.
        val y: List<Double>,
        // Run extent: the widest row's summed margin-box widths.
        val width: Double,
        // Σ row heights, floored at [strutPx] (P6) when > 0.
        val height: Double,
    )

    /**
     * P3/P4/P5 — greedy §9.5.1 packing of one run.
     *
     * Cursor packing (rules 1-3): each float sits at the current x and
     * advances by its margin-box width. Wrap (rule 7): when the float
     * does not fit the remaining [availableWidth] AND it is not the
     * first in its row, it starts a new row (a row's first float may
     * overflow — rule 7's "as far left as possible" loose case).
     * [availableWidth] is +∞ in the composed-WPT adapters (P4): the
     * browser-ref laid these floats against the BODY (≥360px) while the
     * captured IR wraps roots in synthetic 100px frames — packing at the
     * artifact width would reproduce browser-at-100px geometry, not the
     * ref. Finite widths stay exercised by the pinning suites.
     * [strutPx] > 0 floors the reported height (P6) for strutted runs;
     * pass 0.0 for un-strutted runs (the abspos shrink-to-fit case).
     */
    fun layout(
        widths: List<Double>,
        heights: List<Double>,
        availableWidth: Double,
        strutPx: Double,
    ): RunLayout {
        // Per-child row assignment + x origin from the greedy cursor.
        val rowOf = IntArray(widths.size)
        val xs = MutableList(widths.size) { 0.0 }
        var x = 0.0
        var row = 0
        widths.forEachIndexed { i, w ->
            // Rule 7 wrap: only a NON-first float wraps; x > 0 encodes
            // "someone already sits in this row".
            if (x > 0.0 && x + w > availableWidth) { row += 1; x = 0.0 }
            xs[i] = x
            rowOf[i] = row
            // Rule 2: the next float packs against this one's right
            // margin edge (widths are margin-box widths).
            x += w
        }
        // P5 — row height = max child margin-box height in the row.
        val rowHeights = DoubleArray(row + 1)
        widths.indices.forEach { i ->
            if (heights[i] > rowHeights[rowOf[i]]) rowHeights[rowOf[i]] = heights[i]
        }
        // P5 — row tops are the running sum of previous row heights.
        val rowTops = DoubleArray(row + 1)
        for (r in 1..row) rowTops[r] = rowTops[r - 1] + rowHeights[r - 1]
        // Per-child y = its row's top edge (floats align to the row top —
        // §9.5.1 rule 6: no higher than preceding floats' tops).
        val ys = widths.indices.map { rowTops[rowOf[it]] }
        // P5 — run width = the widest row's extent (Σ widths per row).
        val rowExtents = DoubleArray(row + 1)
        widths.indices.forEach { i -> rowExtents[rowOf[i]] += widths[i] }
        val width = rowExtents.maxOrNull() ?: 0.0
        // P6 — strut floor on the total height for strutted runs.
        val height = maxOf(rowTops[row] + rowHeights[row], strutPx)
        return RunLayout(x = xs, y = ys, width = width, height = height)
    }
}
