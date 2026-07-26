package com.styleconverter.runtime.layout

// FloatRowPacking — the PURE half of CSS 2.1 §9.5 float row packing
// (wave-19 lane FLOAT, extended by wave-20 lane W3). Byte-parallel twin
// of iOS StyleEngine/layout/FloatRowPacking.swift — same names, same
// pinned semantics (P1-P6 wave-19, P10-P14 wave-20); skeptic probes
// diff the two.
//
// Scope (narrow, corpus-sufficient — see the lane contracts):
//  • F1: consecutive same-direction LEFT floats pack into a horizontal
//    run, wrapping at width exhaustion (§9.5.1 rules 1-3,7 — greedy,
//    no interleaving with in-flow line boxes).
//  • F2: a childless clear sibling (the `<br clear>` wire shape) breaks
//    the run and marks it STRUTTED (§9.5.2 clearance + the br's empty
//    line box in the composed-WPT ref).
//  • F4 (wave-20 W3 revision): consecutive same-direction RIGHT floats
//    now ALSO pack — right-to-left from the containing block's right
//    edge (§9.5.1 rule 1 mirrored; css-grid descendant-static-position-
//    002/004's float:right pairs). Lone right floats and non-float
//    siblings keep the frozen paths (wave-5 end-alignment / block flow).
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
     * [floatsRight]/[clearBreaksRight] default false so every wave-19
     * call site (and its pinned constructors) is byte-compatible.
     */
    data class ChildFacts(
        // P1: Float ∈ {LEFT, INLINE_START} — the LTR-normalized engine
        // treats inline-start as left (css-logical-1 §2.1). Under an
        // rtl container the logical members swap (see [facts]).
        val floatsLeft: Boolean,
        // P2: childless + Float=NONE + Clear ∈ {BOTH, LEFT, INLINE_START}
        // — the `<br clear>` IR shape. Clear RIGHT/INLINE_END does NOT
        // clear a left run (§9.5.2: clearance only past same-side floats).
        val clearBreaksLeft: Boolean,
        // P10: Float ∈ {RIGHT, INLINE_END} (ltr) — the wave-20 right-run
        // family. Mutually exclusive with [floatsLeft] by construction.
        val floatsRight: Boolean = false,
        // P14: the §9.5.2 mirror of [clearBreaksLeft] — childless +
        // Float=NONE + Clear ∈ {BOTH, RIGHT, INLINE_END} (ltr).
        val clearBreaksRight: Boolean = false,
    )

    /**
     * Derive [ChildFacts] from an extracted [FloatConfig]. Kept pure over
     * the typed config (not raw IR) so both call sites — the renderer and
     * the JVM tests — share one derivation.
     * [hasChildren] guards the clear-break shape: a clear on a CONTENT
     * box is real layout (out of the narrow contract), only the childless
     * `<br>` marker is a pure row break.
     * [rtl] maps the logical keywords per css-logical-1 §2.1: under
     * direction:rtl inline-start→right and inline-end→left (wave-20 W3;
     * defaults false so wave-19 call sites stay LTR-normalized verbatim).
     */
    fun facts(config: FloatConfig, hasChildren: Boolean, rtl: Boolean = false): ChildFacts {
        // css-logical-1 §2.1 — the physical side each logical keyword
        // resolves to under the container's direction.
        val startIsLeft = !rtl
        // P1 — left-family floats: physical LEFT plus whichever logical
        // keyword maps to left under [rtl].
        val left = config.float == FloatValue.LEFT ||
            (config.float == FloatValue.INLINE_START && startIsLeft) ||
            (config.float == FloatValue.INLINE_END && !startIsLeft)
        // P10 — right-family floats: the exact mirror.
        val right = config.float == FloatValue.RIGHT ||
            (config.float == FloatValue.INLINE_END && startIsLeft) ||
            (config.float == FloatValue.INLINE_START && !startIsLeft)
        // P2/P14 — the break marker must not itself float and must be
        // childless (the wire's `<br clear>` has neither).
        val marker = !hasChildren && config.float == FloatValue.NONE
        return ChildFacts(
            floatsLeft = left,
            // §9.5.2: clearance only past same-side floats — BOTH clears
            // either side, the logical members resolve per [rtl].
            clearBreaksLeft = marker &&
                (config.clear == ClearValue.BOTH || config.clear == ClearValue.LEFT ||
                    (config.clear == ClearValue.INLINE_START && startIsLeft) ||
                    (config.clear == ClearValue.INLINE_END && !startIsLeft)),
            floatsRight = right,
            clearBreaksRight = marker &&
                (config.clear == ClearValue.BOTH || config.clear == ClearValue.RIGHT ||
                    (config.clear == ClearValue.INLINE_END && startIsLeft) ||
                    (config.clear == ClearValue.INLINE_START && !startIsLeft)),
        )
    }

    /**
     * One segment of the child list, in sibling order: either a float
     * RUN (≥2 consecutive same-side floats, rendered side-by-side by the
     * adapter) or a SINGLE child that keeps the block-flow path.
     * [rightRun] defaults false so wave-19 pinned Segment values compare
     * byte-identically.
     */
    data class Segment(
        // Child indices (into the original sibling list) in order.
        val indices: List<Int>,
        // True for a packable float run (always ≥2 indices then).
        val isRun: Boolean,
        // P2/P6: the sibling immediately after the run is a clear-break
        // marker — the adapter applies the line-box strut to this run.
        val strutted: Boolean,
        // P11: true when the run packs right-to-left from the containing
        // block's right edge (§9.5.1 rule 1 mirrored, wave-20 W3).
        val rightRun: Boolean = false,
    )

    /**
     * P1/P11 — split the sibling list into maximal float runs and singles.
     * A run is ≥2 CONSECUTIVE same-side floating siblings; anything else
     * (clear marker, opposite-side float, in-flow box) ends the streak
     * and renders as a single. Lone floats stay singles too — today's
     * path already renders one float correctly, and keeping it identical
     * minimizes the gated surface (F4 conservatism, both sides).
     */
    fun segment(children: List<ChildFacts>): List<Segment> {
        // Output accumulator — segments in sibling order.
        val out = mutableListOf<Segment>()
        // Current streak of consecutive same-side float indices.
        val streak = mutableListOf<Int>()
        // The streak's side (true = right-family); meaningless when empty.
        var streakRight = false
        // Flush the open streak: a run when ≥2, singles otherwise.
        // [next] is the index AFTER the streak (or null at the end) —
        // it decides the strut (P2/P14: a trailing same-side clear-break).
        fun flush(next: Int?) {
            if (streak.size >= 2) {
                // The strut fires only when the run is terminated by a
                // SAME-SIDE clear-break sibling (§9.5.2's same-side rule;
                // the `<br clear>` line box, P6).
                val strut = next != null && (
                    if (streakRight) children[next].clearBreaksRight
                    else children[next].clearBreaksLeft
                    )
                out.add(Segment(streak.toList(), isRun = true, strutted = strut, rightRun = streakRight))
            } else {
                // 0 or 1 floats — each keeps the plain block path.
                streak.forEach { out.add(Segment(listOf(it), isRun = false, strutted = false)) }
            }
            streak.clear()
        }
        // Single pass over the siblings, building same-side streaks.
        children.forEachIndexed { i, c ->
            if (c.floatsLeft || c.floatsRight) {
                // A side flip ends the current streak — left and right
                // runs never merge (§9.5.1 rule 1 vs its mirror).
                if (streak.isNotEmpty() && streakRight != c.floatsRight) flush(i)
                // Extend (or begin) the streak on this child's side.
                streakRight = c.floatsRight
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

    /**
     * P12 — the RIGHT-run mirror of [layout] (wave-20 W3, CSS 2.1
     * §9.5.1 rule 1 mirrored: a right float's RIGHT outer edge sits
     * against the containing block's right edge, the next one packs to
     * its LEFT). Implemented as a pure reflection of the greedy left
     * plan against the run extent — row assignment, wrap rule, row
     * heights and the strut floor are shared byte-for-byte, so every
     * left-side pin transfers: x'[i] = width − x[i] − w[i]. Each row
     * right-aligns at the run's right edge (rule 1 per row); the adapter
     * then anchors that edge at the containing block's right edge (P13).
     */
    fun layoutEnd(
        widths: List<Double>,
        heights: List<Double>,
        availableWidth: Double,
        strutPx: Double,
    ): RunLayout {
        // The shared greedy plan — identical rows/heights/extent (P12).
        val base = layout(widths, heights, availableWidth, strutPx)
        // Mirror each x against the run extent: first float flush right,
        // successors extend leftward (§9.5.1 rules 1-3 mirrored).
        val xs = widths.indices.map { base.width - base.x[it] - widths[it] }
        return RunLayout(x = xs, y = base.y, width = base.width, height = base.height)
    }
}
