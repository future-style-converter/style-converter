package com.styleconverter.runtime.core.renderer

// Compose runtime — wave 48 lane W1: EXPLICIT intrinsics for the two
// vertical-flow layouts, closing the LargeDimension poisoning that
// composed every vertical-writing-mode root to 8264 px.
//
// ## The measured defect (private API-36.1 emulator, css-writing-modes/
// ## direction-upright-002)
// Both vertical-flow composables previously used the trailing-lambda
// `Layout` overload, whose MeasurePolicy inherits the DEFAULT intrinsic
// implementations: they re-run the measure lambda against
// `DefaultIntrinsicMeasurable` fakes whose `measure()` returns a
// fixed-size placeable substituting `LargeDimension` = 2^15−1 = 32767
// for any UNBOUNDED axis (androidx.compose.ui.layout.Layout.kt — the
// fake must encode a finite size, so the platform picks a huge one).
// A horizontal layout never echoes that sentinel: it reports the fake's
// width for a width query and the 32767 rides only the axis nobody
// asked about. The rotated run's AXIS SWAP breaks exactly that
// assumption — it reports `width = child.height` — so a
// `maxIntrinsicWidth` query against a rotated "A" answered 32767.
// Probe-logged chain on device: each <td>'s max-content width 32769 →
// the table's §17.5.2 shrink-to-fit sum 98311 → the row's §17.5.3
// min-intrinsic height 32769 → IntrinsicChannel.fixedBandPackable caps
// it at packableCap(98311) = 8190 (the wave-47 crash trio, now capped
// instead of thrown) → every vertical root 8264 px tall → the composed
// canvas 66404 px, which exceeds the emulator's ~8192 px GPU
// render-target limit, so GraphicsLayer.toImageBitmap() rasterised a
// fully TRANSPARENT bitmap — the css-writing-modes cell shipped
// unmeasured. This is precisely the hardening IntrinsicChannel's
// "EXACTNESS SCOPE" banner predicted: hand the swapped layouts explicit
// intrinsic overrides mirroring the platform's node pairs.

// MeasurePolicy is implemented as an explicit object so the four
// intrinsic entry points can be overridden; the measure bodies are the
// former trailing lambdas moved verbatim.
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import com.styleconverter.runtime.typography.text.LineStack
import com.styleconverter.runtime.typography.text.VerticalTextFlow

/**
 * Measure policy for [VerticalRotatedTextRun]: the wave-5 swap/coerce/
 * centre-rotate measure, verbatim, plus the TRANSPOSED intrinsics.
 *
 * The transposition table follows from the measure contract alone
 * (wrapper.width = child.height, wrapper.height = child.width, child's
 * width budget = wrapper's height budget and vice versa):
 *
 *   wrapper.minIntrinsicWidth(h)  = child.minIntrinsicHeight(h)
 *   wrapper.maxIntrinsicWidth(h)  = child.maxIntrinsicHeight(h)
 *   wrapper.minIntrinsicHeight(w) = child.minIntrinsicWidth(w)
 *   wrapper.maxIntrinsicHeight(w) = child.maxIntrinsicWidth(w)
 *
 * — each query hands the HINT straight through, because the hint arrives
 * in the wrapper's frame (a width query carries a height budget) and the
 * swap maps it onto the child's PERPENDICULAR axis, which is the axis
 * the child's transposed query expects a budget for. No fake measure
 * pass, no LargeDimension anywhere.
 */
internal fun rotatedRunMeasurePolicy(rotationDegrees: Float): MeasurePolicy =
    object : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints
        ): MeasureResult {
            // Swap the axes: the text's inline axis runs along the box's
            // block axis, so its wrap width is the incoming HEIGHT budget
            // (unbounded → let it be a single line). Byte-identical to the
            // pre-wave-48 lambda.
            val swapped = Constraints(
                minWidth = 0,
                maxWidth = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity,
                minHeight = 0,
                maxHeight = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity,
            )
            val placeable = measurables.first().measure(swapped)
            // Report the rotated footprint (width↔height swapped).
            val w = placeable.height.coerceIn(constraints.minWidth, constraints.maxWidth)
            val h = placeable.width.coerceIn(constraints.minHeight, constraints.maxHeight)
            return layout(w, h) {
                // Center-rotate: place the child so its center lands at the
                // wrapper's center, then spin it about that center.
                val x = (w - placeable.width) / 2
                val y = (h - placeable.height) / 2
                placeable.placeWithLayer(x, y) {
                    rotationZ = rotationDegrees
                }
            }
        }

        // ── The transposed intrinsic quartet (table in the class doc) ──
        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>, height: Int
        ): Int = measurables.first().minIntrinsicHeight(height)

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>, height: Int
        ): Int = measurables.first().maxIntrinsicHeight(height)

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>, width: Int
        ): Int = measurables.first().minIntrinsicWidth(width)

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>, width: Int
        ): Int = measurables.first().maxIntrinsicWidth(width)
    }

/**
 * Measure policy for [VerticalUprightTextFlow]: the wave-35 plan/decline
 * measure, verbatim, plus intrinsics computed from the GLYPH slots — the
 * default lambda-replay was poisoned the same way as the rotated run's
 * (its fallback slot is a rotated run, and its glyph fakes each answered
 * 32767 on their unbounded axis).
 *
 * Intrinsic model (css-writing-modes-4 §7.3: the inline axis is
 * VERTICAL, lines stack across the width):
 *  - min WIDTH  = the widest single glyph — the narrowest the flow can
 *    be is one column, and a column is as wide as its widest glyph
 *    (css-sizing-3 §5.1 min-content: the largest unbreakable unit).
 *  - max WIDTH(heightBudget) = the planned column count × widest glyph:
 *    the SAME wrap plan [VerticalTextFlow.uprightColumnIndices] the
 *    measure runs, fed the SAME first-glyph advance rule (rationale on
 *    maxIntrinsicWidth), so at one shared bounded budget the two agree
 *    whenever each glyph slot's intrinsic advance equals its measured
 *    height — true for the one-code-point Text slots both are built
 *    from, but a per-child property, not a theorem (wave-48 S4
 *    softening: the old unconditional "can never disagree" claim was
 *    false — this query read the MAX advance while the measure read the
 *    FIRST, so heterogeneous advances planned different columns). An
 *    unbounded budget is one column (max-content: no wrapping) — there
 *    intrinsic and measure part ways ON PURPOSE, because the measure
 *    declines an unbounded block axis to the fallback rather than
 *    invent a wrap width, while a max-content query wants the flow's
 *    own unwrapped extent.
 *  - min HEIGHT = the tallest single glyph (a column may break after
 *    every glyph, so no line box needs more than one glyph's advance).
 *  - max HEIGHT = the sum of every glyph's advance — the single
 *    unwrapped column (max-content inline extent).
 *  - FALLBACK-OWNED runs (wave-48 S4): when the planner declines the
 *    run's CONTENT at every budget — glyphless, all-collapsible-space,
 *    or zero-advance (see [fallbackOwnsRun]) — the measure lays slot 0
 *    (the rotated fallback) out UNSWAPPED, so all four intrinsics defer
 *    to the fallback's own SAME-AXIS answers instead of quoting glyph
 *    metrics nothing will ever render.
 *
 * Glyph advances are read through the CHILD intrinsic channel
 * (`maxIntrinsicHeight(Infinity)` per glyph — a one-code-point Text has
 * one line box, whose height IS the advance), not through fake measures.
 *
 * @param glyphs the SAME per-code-point list the composable's slots
 *   1…n were built from — index parity with the measurables is the
 *   contract that lets the plan address them.
 */
internal fun uprightFlowMeasurePolicy(
    glyphs: List<String>,
    stack: LineStack,
    onDecline: () -> Unit,
): MeasurePolicy = object : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        // EVERY measurable is measured exactly once, on BOTH paths —
        // including the fallback the plan path never places (a Measurable
        // may be measured at most once per pass; an unplaced placeable is
        // never drawn). Verbatim from the pre-wave-48 lambda.
        val fallback = measurables.first().measure(constraints)
        // Every glyph measures FREE: an upright glyph is its own line box
        // and is never squeezed by the run's box (CSS overflows instead).
        val free = Constraints()
        val glyphPlaceables = measurables.drop(1).map { it.measure(free) }
        // The advance along the vertical inline axis = one line box's height.
        val advance = glyphPlaceables.firstOrNull()?.height?.toDouble() ?: 0.0
        // The wrap budget IS the incoming height constraint — null when the
        // block axis is unbounded, which the planner declines on.
        val budget = if (constraints.hasBoundedHeight) constraints.maxHeight.toDouble() else null
        val plan = VerticalTextFlow.uprightColumnIndices(glyphs, advance, budget)

        if (plan == null) {
            // No silent fallthrough: the GATE already said this run is
            // upright, so a decline here is a real, named gap — the caller
            // logs it once per process (see VerticalTextFlowLayout).
            onDecline()
            // DECLINE — hand the frame back to the frozen sideways path.
            return layout(fallback.width, fallback.height) { fallback.place(0, 0) }
        }
        // Per-line cross extent (the line box's width) and main extent
        // (the sum of its glyph advances).
        val lineW = plan.map { line -> line.maxOf { glyphPlaceables[it].width } }
        val lineH = plan.map { line -> line.sumOf { glyphPlaceables[it].height } }
        val totalW = lineW.sum()
        val totalH = lineH.maxOrNull() ?: 0
        val w = totalW.coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = totalH.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(w, h) {
            // `vertical-rl` puts line 1 at the RIGHT edge and walks left;
            // `vertical-lr` starts at the left edge and walks right. Both
            // walk the plan in LOGICAL order — only the anchor differs.
            var x = if (stack == LineStack.RIGHT_TO_LEFT) totalW else 0
            plan.forEachIndexed { index, line ->
                if (stack == LineStack.RIGHT_TO_LEFT) x -= lineW[index]
                var y = 0
                for (glyphIndex in line) {
                    val p = glyphPlaceables[glyphIndex]
                    // Centre the glyph across its line box (no-op when every
                    // glyph in the line shares one advance — every real
                    // upright run).
                    p.place(x + (lineW[index] - p.width) / 2, y)
                    y += p.height
                }
                if (stack == LineStack.LEFT_TO_RIGHT) x += lineW[index]
            }
        }
    }

    /** One glyph's advance: its single line box's height, read through
     *  the intrinsic channel with an unbounded width hint (a one-code-
     *  point Text never wraps, so the hint is irrelevant but must be
     *  passed). Slot 0 is the fallback — glyphs are slots 1…n. */
    private fun glyphAdvances(measurables: List<IntrinsicMeasurable>): List<Int> =
        measurables.drop(1).map { it.maxIntrinsicHeight(Constraints.Infinity) }

    /** One glyph's cross extent: its max-content width. */
    private fun glyphWidths(measurables: List<IntrinsicMeasurable>): List<Int> =
        measurables.drop(1).map { it.maxIntrinsicWidth(Constraints.Infinity) }

    /**
     * TRUE when [VerticalTextFlow.uprightColumnIndices] declines this
     * run at EVERY budget — i.e. the measure lays the FALLBACK slot out
     * whatever constraints arrive, so the fallback owns the intrinsic
     * answers too. Wave-48 S4 executed the incoherence this closes: an
     * all-space run whose measure renders the fallback at 200×40 had
     * intrinsics quoting glyph metrics (maxW=5, maxH=36), and the old
     * glyphless elvis arms TRANSPOSED the fallback's answers on top of
     * its own already-transposed rotated quartet (a double swap).
     *
     * The predicate is the planner ITSELF — not a re-derived twin rule
     * that could drift — probed at the single-column budget
     * `glyphCount × firstAdvance`: capacity there is the whole run, so
     * the budget-DEPENDENT decline (the MAX_COLUMNS guard) cannot fire,
     * leaving exactly the budget-independent nulls the planner
     * documents — a glyphless run, a non-positive advance (both make
     * the probe budget ≤ 0, which also declines), and an
     * all-collapsible-space run (css-text-3 §4.1.3 collapsing empties
     * every column).
     */
    private fun fallbackOwnsRun(advances: List<Int>): Boolean {
        // The ONE advance the measure would read — the FIRST glyph's
        // (unified in wave-48 S4; rationale on maxIntrinsicWidth).
        val advance = advances.firstOrNull()?.toDouble() ?: 0.0
        // Single-column probe budget (see the KDoc above).
        return VerticalTextFlow.uprightColumnIndices(glyphs, advance, glyphs.size * advance) == null
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>, height: Int
    ): Int {
        val advances = glyphAdvances(measurables)
        // Fallback-owned run: the decline measure lays slot 0 out
        // UNSWAPPED (`layout(fallback.width, fallback.height)`), so its
        // SAME-AXIS answer is the only one coherent with what renders —
        // the pre-wave-48 arm asked the fallback minIntrinsicHeight
        // here, transposing its already-transposed rotated quartet a
        // second time (S4 probe: answered 107 for a 101-wide fallback).
        if (fallbackOwnsRun(advances)) return measurables.first().minIntrinsicWidth(height)
        // Narrowest layout = one column = the widest single glyph (class
        // doc); non-empty, because a glyphless run is fallback-owned.
        return glyphWidths(measurables).max()
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>, height: Int
    ): Int {
        val advances = glyphAdvances(measurables)
        // Fallback-owned run: same-axis fallback answer (rationale on
        // minIntrinsicWidth — this arm used to ask maxIntrinsicHeight).
        if (fallbackOwnsRun(advances)) return measurables.first().maxIntrinsicWidth(height)
        val widths = glyphWidths(measurables)
        // The ONE advance describing the run is the FIRST glyph's,
        // because that is what the measure reads
        // (`glyphPlaceables.firstOrNull()?.height`) — wave-48 S4
        // unification: the old MAX-advance read planned DIFFERENT
        // columns than the measure on heterogeneous (font-fallback)
        // advances — 20 px + 30 px glyphs at budget 50 planned
        // [[0], [1]] here vs [[0, 1]] in the measure. FIRST wins
        // because coherence with the layout that actually renders is
        // the property intrinsics exist to provide, and upright
        // vertical typesetting gives every glyph the same em-square
        // advance anyway (the planner's own contract), so real runs
        // cannot tell the difference.
        val advance = advances.first().toDouble()
        // Unbounded hint = max-content: no wrapping, one unwrapped
        // column, the widest glyph (css-sizing-3 §5.1). The planner is
        // deliberately NOT consulted here — it declines unbounded
        // budgets, which for the MEASURE means the fallback path, but a
        // max-content QUERY wants the flow's own unwrapped extent (the
        // class-doc bullet on where intrinsic and measure part ways).
        if (height == Constraints.Infinity) return widths.max()
        // Bounded hint: the SAME wrap plan the measure runs at this
        // budget. Content declines were handled above, so a null here
        // is a budget the measure itself would decline to the fallback
        // on (non-positive, or past the MAX_COLUMNS guard) — the
        // fallback answers, same axis.
        val plan = VerticalTextFlow.uprightColumnIndices(glyphs, advance, height.toDouble())
            ?: return measurables.first().maxIntrinsicWidth(height)
        // Planned: sum each column's widest glyph — the measure's own
        // totalW arithmetic, applied to intrinsic widths.
        return plan.sumOf { line -> line.maxOf { widths[it] } }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>, width: Int
    ): Int {
        val advances = glyphAdvances(measurables)
        // Fallback-owned run: same-axis fallback answer (rationale on
        // minIntrinsicWidth — this arm used to ask the fallback's
        // minIntrinsicWidth for a HEIGHT query).
        if (fallbackOwnsRun(advances)) return measurables.first().minIntrinsicHeight(width)
        // A column may break after every glyph: the tallest single
        // glyph is the minimum inline extent (class doc).
        return advances.max()
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>, width: Int
    ): Int {
        val advances = glyphAdvances(measurables)
        // Fallback-owned run: same-axis fallback answer (rationale on
        // minIntrinsicWidth).
        if (fallbackOwnsRun(advances)) return measurables.first().maxIntrinsicHeight(width)
        // The single unwrapped column: every glyph advance, summed.
        return advances.sum()
    }
}
