// Pure strip geometry for the wave-44 lane-U8 FLOAT STRIP (classification:
// MulticolFloatStrip.kt) — no Compose imports on purpose: the JUnit FS
// table pins this math on the plain JVM and the IDENTICAL rows are pinned
// by the iOS twin (runtimes/swiftui .../columns/MulticolFloatStrip.swift).
//
// Geometry, hand-derived from the frozen Chromium refs (tools/wpt/refs/
// 9b5435…/CSS2/floats-clear__floats-clear-multicol-*.png, content-box
// coordinates):
//  · fill (column-fill:auto, H=100): floats occupy strip [0,250) → columns
//    slice 100+100+50 (aqua rows 111-210/111-210/111-160); the cleared box
//    lands at strip 250 → its 3px orange border paints at rows 161-163 of
//    column 3. C=253, H=100, 3 fragments.
//  · balancing (initial column-fill:balance, definite height 100): Chromium
//    balances the SAME strip into H=ceil(255/3)=85 columns (aqua rows
//    91-175/91-175/91-170, 5px orange rows 171-175) — the 5px trailing
//    border ink is part of the balanced extent, hence trailingInkPx.
package com.styleconverter.runtime.columns

object MulticolFloatStripPlan {

    /** The strip answer: child offsets + used column size + replay slices. */
    data class StripPlan(
        /**
         * Block offset of each child in the CONTINUOUS strip (column-0
         * coordinates), index-aligned with the inputs — the place()
         * targets. The flush cursor stack, except where §9.5.2 clearance
         * pushed a cleared child down to the relevant floats' bottom edge.
         */
        val yOffsetsPx: List<Int>,
        /**
         * The used column block-size the fragments slice with: the
         * definite H under `column-fill: auto` (§7.2 sequential fill),
         * min(H, ceil(C/N)) under `balance` (§7.1 reduced to the stacked
         * strip — the exact 85 the balancing refs paint).
         */
        val columnBlockSizePx: Int,
        /** C — the strip's ink extent (flow bottom ∨ float bottoms ∨ ink). */
        val stripInkPx: Int,
        /**
         * The css-break-3 §4 slices over the whole strip (empty when the
         * strip fits one column) — same clip+translate replay the wave-10
         * and wave-42 run passes feed the container's drawWithContent.
         */
        val fragments: List<FragmentGeometry.Fragment>
    )

    /**
     * The IR-provable floor on the strip's ink extent C (px), rounded the
     * SAME way [plan] rounds its own terms: the tallest declared float
     * height or trailing-ink band over the children. MEASURED child heights
     * are deliberately absent — this is exactly the half of C that is
     * knowable BEFORE a measure pass, which is what makes it usable as a
     * gate (see [engagesPreMeasure]).
     */
    fun provableStripInkPx(specs: List<MulticolSpannerFlow.ChildSpec>?): Int {
        // No specs / no facts ⇒ nothing provable (engagement rejects those
        // shapes anyway; 0 just keeps this function total).
        if (specs.isNullOrEmpty()) return 0
        // Both IR-derived terms of C: a float's declared height extends its
        // side ledger (`y + round(heightPx)` below, y ≥ 0) and a child's
        // trailing ink extends the paint floor (`y + round(trailingInkPx)`),
        // so either one rounding to ≥ 1 already forces C ≥ 1.
        return specs.maxOf { s ->
            val f = s.floatStrip ?: return@maxOf 0
            maxOf(
                f.floats.maxOfOrNull { Math.round(it.heightPx).toInt() } ?: 0,
                Math.round(f.trailingInkPx).toInt()
            )
        }
    }

    /**
     * The PRE-MEASURE strip decision — the single predicate BOTH halves
     * gate on (composition: [MulticolFloatStrip.zeroFlowPlan]; measure:
     * MulticolFloatStripMeasure), so the paint half can never zero-flow
     * floats under a layout that still stacks them.
     *
     * Why the fill mode participates (wave-44 skeptic S5, D1): under §7.1
     * balance the used column size below is `min(H, ceil(C/N))`, which is 0
     * — [plan]'s one degenerate decline — exactly when C == 0, and C == 0
     * additionally needs every MEASURED child height to be 0. That last
     * term is unknowable before measuring, and answering AFTER the measure
     * is not an option: the measure half's decline path returns to
     * MultiColumnApplier's legacy loop, which measures the SAME measurables
     * a second time — Compose permits one measure per Measurable per pass
     * and throws IllegalStateException on the second, so a post-measure
     * decline would CRASH the capture rather than keep the legacy layout.
     * The answer here is therefore the conservative one: under balance the
     * strip engages only when the IR alone proves C ≥ 1
     * ([provableStripInkPx]); under §7.2 fill the used size is the definite
     * H, which every caller already gates > 0.
     *
     * What the conservative side costs, stated plainly: a balance-mode
     * container whose floats ALL declare height 0 and whose children carry
     * no trailing ink keeps the legacy layout even if its children measure
     * tall. Such a float paints nothing at all — [MulticolFloatStrip.factsFor]
     * rejects any float carrying borders/padding/margins, children or text,
     * so a 0-height float has no ink — hence the strip model would have
     * added fragmentation only, never float geometry, on a shape no ref in
     * the proven floats-clear-multicol family exhibits.
     */
    fun engagesPreMeasure(
        specs: List<MulticolSpannerFlow.ChildSpec>?,
        columnFillAuto: Boolean
    ): Boolean {
        // Shape first: everything MulticolFloatStrip.engages rejects (roles,
        // unproven facts, no real float) is rejected here too.
        if (!MulticolFloatStrip.engages(specs)) return false
        // §7.2 sequential fill keeps the definite H — no C-dependent size,
        // so nothing else can turn degenerate after the measure.
        if (columnFillAuto) return true
        // §7.1 balance: only a provable ink floor guarantees h ≥ 1.
        return provableStripInkPx(specs) > 0
    }

    /**
     * Build the strip plan for an ENGAGED container (callers gate through
     * [engagesPreMeasure], which subsumes [MulticolFloatStrip.engages] and
     * proves this function cannot decline; measured heights come from the zero-
     * flow measure, so a float container contributes only its in-flow
     * extent — 0 when floats are its whole content).
     *
     * @param specs per-child specs whose [MulticolSpannerFlow.ChildSpec.floatStrip]
     *   facts are all non-null (the engage gate proved it).
     * @param measuredHeightsPx measured child block-sizes at column width
     *   under the zero-flow plan, in child order.
     * @param columnBlockSizePx H — the definite column block-size.
     * @param columnFillAuto true iff the container declares `column-fill:
     *   auto` (§7.2); false = the initial `balance` (§7.1).
     * @param columnWidthPx W / @param columnGapPx G / @param columnCount N
     *   — the §3.4 used column geometry.
     */
    fun plan(
        specs: List<MulticolSpannerFlow.ChildSpec>,
        measuredHeightsPx: List<Int>,
        columnBlockSizePx: Int,
        columnFillAuto: Boolean,
        columnWidthPx: Int,
        columnGapPx: Int,
        columnCount: Int
    ): StripPlan? {
        // Defensive re-gate: engagement + aligned inputs + a real
        // fragmentainer + ≥2 columns (N==1 keeps the overflow-not-clip
        // semantics of the legacy path — the run pass's N==1 rationale).
        if (!MulticolFloatStrip.engages(specs)) return null
        if (specs.size != measuredHeightsPx.size) return null
        if (columnBlockSizePx <= 0 || columnCount <= 1) return null
        // The strip walk: flush cursor stacking (CSS 2.1 §9.4.1) with
        // §9.5.2 clearance jumps and per-side float-bottom ledgers.
        var cursor = 0
        var leftBottom = 0
        var rightBottom = 0
        var ink = 0
        val offsets = ArrayList<Int>(specs.size)
        specs.forEachIndexed { i, s ->
            // Engage proved every fact non-null.
            val f = s.floatStrip!!
            // §9.5.2: a cleared box's top border edge lands at the
            // relevant floats' bottom outer edge when the flush position
            // has not passed it (the greater-of rule).
            var y = cursor
            if (f.clearsLeft) y = maxOf(y, leftBottom)
            if (f.clearsRight) y = maxOf(y, rightBottom)
            offsets.add(y)
            // Anchor this child's leading floats at its content top and
            // extend the per-side ledgers (§9.5: the floats are out of
            // flow, so only the ledgers — never the cursor — grow).
            for (fl in f.floats) {
                val bottom = y + Math.round(fl.heightPx).toInt()
                if (fl.rightSide) rightBottom = maxOf(rightBottom, bottom)
                else leftBottom = maxOf(leftBottom, bottom)
            }
            // The cursor advances by the measured in-flow extent only.
            cursor = y + maxOf(0, measuredHeightsPx[i])
            // Ink floor: the measured extent or the IR-derived paint floor
            // (whichever is taller) — the height:0 cleared box's inner
            // border band paints past its measured 0 (css-overflow-3 §2).
            ink = maxOf(ink, y + maxOf(measuredHeightsPx[i], Math.round(f.trailingInkPx).toInt()))
        }
        // C: flow bottom ∨ float bottoms ∨ trailing ink.
        val c = maxOf(ink, cursor, leftBottom, rightBottom)
        // Used column block-size: §7.2 sequential fill keeps the definite
        // H; §7.1 balance reduces to ceil(C/N) for a stacked strip, capped
        // at the definite H (content that cannot fit N balanced columns
        // falls back to the fill geometry and clips like the wave-10 cap).
        val h = if (columnFillAuto) columnBlockSizePx
        else minOf(columnBlockSizePx, (c + columnCount - 1) / columnCount)
        // A degenerate strip (h = 0, i.e. balance mode with C == 0: every
        // float height 0, no trailing ink AND every measured height 0) has
        // no geometry. UNREACHABLE from the Compose measure pass by
        // construction — [engagesPreMeasure] already declined that shape
        // BEFORE anything was measured, because a decline here would send
        // the caller back into a second measure of the same measurables
        // (Compose: one measure per Measurable per pass). Kept as the pure
        // function's own total answer, and pinned as such by the FS table.
        if (h <= 0) return null
        // Slices via the shared wave-10 geometry (S-table): F = ceil(C/H)
        // capped at N; each clip is the full column band, so trailing ink
        // just past C still paints inside the last column (no browser
        // clips it — css-overflow-3 §2).
        val fragments = if (c > h) FragmentGeometry.fragmentGeometry(
            childBlockSizePx = c,
            columnBlockSizePx = h,
            columnWidthPx = columnWidthPx,
            columnGapPx = columnGapPx,
            columnCount = columnCount
        ) else emptyList()
        return StripPlan(offsets, h, c, fragments)
    }
}
