package com.styleconverter.runtime.layout

// InlineAtomFlow — the PURE half of the wave-20 W3 inline-level atom
// flow: UA form-control widgets (and text-only anchors) that the
// browser lays INLINE-WRAPPED inside a block container (css-ui
// appearance-auto-001's 500px row soup) while the natives block-stacked
// them one per line. Byte-parallel twin of iOS
// StyleEngine/layout/InlineAtomFlow.swift — same names, same pinned
// semantics (P15-P18); skeptic probes diff the two.
//
// Model (CSS 2.1 §9.4.2 inline formatting context, atom-only subset):
//  • atoms are OPAQUE inline-level replaced-ish boxes (inline-block
//    baseline rules, §10.8.1) — no text interleaving, no line breaking
//    inside an atom;
//  • atoms pack left-to-right separated by one collapsed white-space
//    advance (UAWidgetIntrinsics.ATOM_GAP_PX), wrapping greedily at
//    width exhaustion (§9.4.2 line box filling; first-in-row never
//    wraps — the same loose rule the float packer pins);
//  • within a row, atoms align BASELINE-ish (§10.8): each atom carries
//    a descent (baseline distance up from its border-box bottom, from
//    the shared UAWidgetIntrinsics table); the row baseline sits at the
//    max ascent, giving the ref's exact row pitches (P17).
// The Compose adapter lives in InlineFlowLayout.kt; this file has no
// Compose dependency so the JVM pinning suite runs it directly.
object InlineAtomFlow {

    // P15 — the widget tag set of the wave-20 widget-identity contract,
    // plus <textarea>: the contract's attrs channel covers it and the
    // appearance-auto ref packs it inline with the buttons (row 2) —
    // excluding it would split the ref rows. <option> is widget CONTENT
    // (W2's), never a flow atom.
    private val WIDGET_TAGS = setOf(
        "button", "input", "textarea", "select", "meter", "progress"
    )

    /**
     * P15 — the atom predicate. An inline-level atom is:
     *  • a widget tag (above) whose display is not overridden to a
     *    block-level value (css-display-3 §2: declared block/flex/grid/
     *    table/list-item displays leave the inline flow; the UA default
     *    for all these controls is inline-block), or
     *  • an <a> carrying ONLY text (no element children) — plain inline
     *    content the ref lays on the same line as the widgets.
     * [displayKeyword] is the SHOUTY IR Display keyword or null when the
     * component declares none; any non-INLINE-prefixed keyword (BLOCK,
     * FLEX, GRID, TABLE, LIST_ITEM, NONE, …) disqualifies — NONE renders
     * nothing and must keep the frozen path that already handles it.
     */
    fun isAtom(
        tag: String?,
        displayKeyword: String?,
        hasElementChildren: Boolean,
        hasText: Boolean,
    ): Boolean {
        // A declared non-inline display takes the box out of the inline
        // flow (css-display-3 §2) — the frozen block loop owns it.
        if (displayKeyword != null && !displayKeyword.startsWith("INLINE")) return false
        // The tag decides the family (widget vs text-only anchor).
        val t = tag?.lowercase() ?: return false
        // Widget atoms are opaque regardless of children (a <select>'s
        // <option>s are its content, not flow siblings).
        if (t in WIDGET_TAGS) return true
        // Anchors qualify only in the text-only shape (P15) — an <a>
        // wrapping elements is a real inline subtree, out of scope.
        return t == "a" && hasText && !hasElementChildren
    }

    /**
     * One segment of the child list, in sibling order: either an atom
     * RUN (≥2 consecutive inline atoms, packed into rows by the adapter)
     * or a SINGLE child that keeps the frozen block path — the exact
     * shape of FloatRowPacking.Segment so the renderers' segment walks
     * mirror each other.
     */
    data class Segment(
        // Child indices (into the original sibling list) in order.
        val indices: List<Int>,
        // True for a packable atom run (always ≥2 indices then).
        val isRun: Boolean,
    )

    /**
     * P15 — split the sibling list into maximal atom runs and singles.
     * ≥2 CONSECUTIVE atoms form a run; a lone atom stays a single (the
     * block path already renders one control acceptably, and the
     * conservative gate mirrors the float packer's F4 discipline).
     */
    fun segment(atomFlags: List<Boolean>): List<Segment> {
        // Output accumulator — segments in sibling order.
        val out = mutableListOf<Segment>()
        // Current streak of consecutive atom indices.
        val streak = mutableListOf<Int>()
        // Flush the open streak: a run when ≥2, singles otherwise.
        fun flush() {
            if (streak.size >= 2) {
                out.add(Segment(streak.toList(), isRun = true))
            } else {
                // 0 or 1 atoms — each keeps the plain block path.
                streak.forEach { out.add(Segment(listOf(it), isRun = false)) }
            }
            streak.clear()
        }
        // Single pass over the siblings, building streaks.
        atomFlags.forEachIndexed { i, isAtom ->
            if (isAtom) {
                // Extend the current atom streak.
                streak.add(i)
            } else {
                // Streak broken — flush, then the breaker stays a single.
                flush()
                out.add(Segment(listOf(i), isRun = false))
            }
        }
        // Trailing streak.
        flush()
        return out
    }

    /** Resolved geometry for one run: per-atom origins + reported size. */
    data class FlowLayout(
        // Per-atom x origin (border-box left edge), run-relative px.
        val x: List<Double>,
        // Per-atom y origin (border-box top edge), run-relative px.
        val y: List<Double>,
        // Widest row's margin-box extent.
        val width: Double,
        // Σ row heights (ascent + descent per row, P17).
        val height: Double,
    )

    /**
     * P16/P17 — greedy inline packing of one atom run.
     *
     * Inputs are BORDER-box sizes plus per-atom [descents] and UA
     * margins (all from the adapter's UAWidgetIntrinsics resolution).
     * Packing (P16): cursor advances by margin-box width; atoms in the
     * same row are separated by [gapPx] (the collapsed source
     * white-space); an atom that would end past [availableWidth] wraps
     * to a new row UNLESS it is the row's first (the same loose
     * first-never-wraps rule the float packer pins — §9.4.2's line box
     * always holds at least one atom).
     * Baselines (P17, §10.8): row ascent A = max(h − descent); atom top
     * y = rowTop + A − (h_i − descent_i); row height = A + max(descent),
     * which reproduces the composed-WPT ref pitches exactly (row 1 → 2
     * pitch 21 = field ascent 15 + descent 6; row 2 → 3 pitch 42 =
     * textarea ascent 36 + button descent 6).
     *
     * Strut (P17b, wave-20 fix 4 — CSS 2.1 §10.8.1): every line box in
     * an IFC contains a zero-width inline box with the block's font
     * metrics, so each row's ascent/descent are FLOORED at
     * [strutAscentPx]/[strutDescentPx]. The adapters pass the shared
     * UAWidgetIntrinsics strut pins (15/5 — the 20px composed-WPT line
     * box); the defaults 0.0 keep every strut-free geometry pin (and
     * any non-corpus caller) byte-identical to wave-20's cut.
     */
    fun layout(
        widths: List<Double>,
        heights: List<Double>,
        descents: List<Double>,
        marginStarts: List<Double>,
        marginEnds: List<Double>,
        availableWidth: Double,
        gapPx: Double,
        strutAscentPx: Double = 0.0,
        strutDescentPx: Double = 0.0,
    ): FlowLayout {
        // Row assignment + border-box x from the greedy cursor.
        val rowOf = IntArray(widths.size)
        val xs = MutableList(widths.size) { 0.0 }
        // Cursor = the current row's consumed extent (margin boxes+gaps).
        var x = 0.0
        var row = 0
        widths.forEachIndexed { i, w ->
            // Margin-box advance for this atom (P16).
            val outer = marginStarts[i] + w + marginEnds[i]
            // A non-first atom needs the inter-atom gap before it.
            val lead = if (x > 0.0) gapPx else 0.0
            // Wrap when the atom would overrun — never for a row-first.
            if (x > 0.0 && x + lead + outer > availableWidth) { row += 1; x = 0.0 }
            // Border-box origin = cursor + (gap when mid-row) + margin.
            xs[i] = x + (if (x > 0.0) gapPx else 0.0) + marginStarts[i]
            rowOf[i] = row
            // Advance past the margin box (+ the gap actually consumed).
            x += (if (x > 0.0) gapPx else 0.0) + outer
        }
        // P17 — per-row ascent/descent maxima over the members.
        val rowAscent = DoubleArray(row + 1)
        val rowDescent = DoubleArray(row + 1)
        // P17b — seed every row with the strut metrics (CSS 2.1 §10.8.1:
        // the line box's zero-width strut participates in the max() like
        // any member); 0.0 defaults make this a no-op for legacy pins.
        rowAscent.fill(strutAscentPx)
        rowDescent.fill(strutDescentPx)
        widths.indices.forEach { i ->
            // Atom ascent = border-box height above its baseline (§10.8.1:
            // inline-block baseline; the table's descent encodes it).
            val ascent = heights[i] - descents[i]
            if (ascent > rowAscent[rowOf[i]]) rowAscent[rowOf[i]] = ascent
            if (descents[i] > rowDescent[rowOf[i]]) rowDescent[rowOf[i]] = descents[i]
        }
        // Row tops = running sum of previous row heights (A + D each).
        val rowTops = DoubleArray(row + 1)
        for (r in 1..row) rowTops[r] = rowTops[r - 1] + rowAscent[r - 1] + rowDescent[r - 1]
        // Atom top: baseline alignment against the row baseline (P17).
        val ys = widths.indices.map { i ->
            rowTops[rowOf[i]] + rowAscent[rowOf[i]] - (heights[i] - descents[i])
        }
        // Extent bookkeeping: widest row's consumed cursor extent.
        val rowExtents = DoubleArray(row + 1)
        widths.indices.forEach { i ->
            // Recompute the row-relative right edge from the atom's
            // border-box origin + width + its end margin.
            val rightEdge = xs[i] + widths[i] + marginEnds[i]
            if (rightEdge > rowExtents[rowOf[i]]) rowExtents[rowOf[i]] = rightEdge
        }
        val width = rowExtents.maxOrNull() ?: 0.0
        // Total height = bottom of the last row.
        val height = rowTops[row] + rowAscent[row] + rowDescent[row]
        return FlowLayout(x = xs, y = ys, width = width, height = height)
    }
}
