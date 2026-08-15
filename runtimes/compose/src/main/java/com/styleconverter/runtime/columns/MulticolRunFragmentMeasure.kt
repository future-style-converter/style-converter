// The Compose half of the wave-42 multi-child run fragmentation: the
// measure-pass integration MultiColumnDistributionLayout delegates to for
// definite-height fill:auto containers with 2+ flow children. Split out of
// MultiColumnApplier.kt (the MulticolSpannerFlowMeasure precedent) so the
// B-RC6 bridge pin — MultiColumnApplier.kt touches fragmentsState.value
// exactly 3 times — stays intact: THIS file's placement block owns the
// run branch's bridge write.
package com.styleconverter.runtime.columns

// The measure→draw snapshot bridge type — written ONLY in the placement
// block below (the wedge lane's B-RC6 contract, same as the spanner twin).
import androidx.compose.runtime.MutableState
// The Layout-pass types this integration bridges between the pure run plan
// and Compose: measurables in, placement commands out.
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
// Constraints.Infinity marks the unbounded child measure — each child lays
// out (and paints) at its natural block-size inside the continuous strip.
import androidx.compose.ui.unit.Constraints

/**
 * Measures + places a definite-height `column-fill: auto` multicol
 * container's children as ONE continuous block run and publishes the
 * css-break-3 §4 slices for the caller's drawWithContent clip+translate
 * replay ([MulticolRunFragment] owns the pure geometry).
 *
 * Returns null whenever the run pass does NOT engage — the caller then
 * falls through to its legacy paths byte-identically. Engagement requires
 * ALL of (each bail below documents its reason; capture gating is inherent
 * because childSpecs is nulled outside WPT capture at the MultiColumnLayout
 * boundary):
 *  - per-measurable specs supplied AND aligned;
 *  - horizontal-tb ([fragmentationAllowed]) — a vertical writing mode
 *    swaps the inline and block axes, so this pass' −i·H block slicing
 *    would cut the run along the WRONG axis (the spanner-flow twin has
 *    carried this parameter since wave-21; the run pass shipped without
 *    it, and the wave-42 skeptic's executed evidence is WPT
 *    css-anchor-position/anchor-position-multicol-007, which declares
 *    `writing-mode: vertical-rl` and passes every other gate below);
 *  - MORE THAN ONE used column ([usedCount] > 1) — see the N==1 gate;
 *  - every child a plain [MulticolSpannerFlow.Role.FLOW] box — spanners
 *    belong to the spanner-flow plan, statics must not replay their ink
 *    into every column, and forced-break children need the chunk model;
 *  - no child hiding a forced column break the role list cannot see
 *    ([MulticolSpannerFlow.ChildSpec.forcedBreakContent] — the child's own
 *    `break-before`, or either break side on a descendant);
 *  - no child subtree declares a float — the natives still stack floats
 *    in flow (the float lane owns css-break-3 §5 / CSS 2.1 §9.5.2), and
 *    slicing that stack would clone a wrong layout into every column
 *    (the floats-clear-multicol family stays on the legacy greedy path
 *    until the float lane lands real out-of-flow geometry);
 *  - `column-fill: auto` — under `balance` the whole-child greedy spread
 *    is the breakpoint-faithful approximation (see MulticolRunFragment's
 *    class doc for the green-cell evidence).
 */
internal object MulticolRunFragmentMeasure {

    fun MeasureScope.measureRunFragment(
        // The Layout's measurables, in RenderContent order.
        measurables: List<Measurable>,
        // The Layout's incoming constraints (post Box loosening).
        constraints: Constraints,
        // Per-measurable role/float specs (null outside capture — inherent
        // dark-stage protection, same convention as the spanner twin).
        childSpecs: List<MulticolSpannerFlow.ChildSpec>?,
        // True iff the container declares `column-fill: auto`.
        columnFillAuto: Boolean,
        // False under vertical writing modes (blocked-platform bail) —
        // threaded exactly like the spanner-flow twin's parameter of the
        // same name, from MultiColumnConfig.verticalWritingMode.
        fragmentationAllowed: Boolean,
        // §3.4 used column count (resolveUsedColumns — always ≥ 1).
        usedCount: Int,
        // §3.4 used per-column width in px (always ≥ 0).
        columnWidthPx: Int,
        // Used column-gap in px.
        gapPx: Int,
        // H — the definite column block-size (the caller's gate proved it).
        columnBlockSizePx: Int,
        // The measure→draw bridge state ([] = plain drawContent). Written
        // ONLY inside the placement block below (B-RC6 contract).
        fragmentsBridge: MutableState<List<FragmentGeometry.Fragment>>,
        // One-shot fallback logger (MultiColumnApplier's shared dedupe).
        logFallback: (String) -> Unit
    ): MeasureResult? {
        // No specs → no IR child view (dark stage by construction) —
        // legacy path, no log (not a fallthrough, same as the spanner twin).
        if (childSpecs == null) return null
        // ── The VERTICAL writing-mode bail (wave-42 skeptic S2) ──────────
        // css-writing-modes-4 §3: a vertical writing mode swaps the inline
        // and block axes, so this pass' stacking (cumulative +y) and its
        // −i·H replay translate would both slice the run along the WRONG
        // axis. The spanner-flow twin has bailed on this since wave-21; the
        // run pass shipped without the parameter and MultiColumnApplier
        // calls it BEFORE its own `!fragmentationAllowed` check, so a
        // vertical container reached the run model unguarded. Measured
        // shape: WPT css-anchor-position/anchor-position-multicol-007
        // (WritingMode VERTICAL_RL, definite height, fill:auto, 2+ plain
        // flow children) passes every remaining gate below.
        if (!fragmentationAllowed) {
            logFallback("run fragmentation: vertical writing-mode (blocked-platform)")
            return null
        }
        // ── The N == 1 overflow-column containment gate ──────────────────
        // With a single used column the §7.2 sequential fill has nowhere to
        // continue: FragmentGeometry caps the fragment list at N, so a run
        // taller than H becomes ONE fragment clipped to H and everything
        // past it is never drawn. That is not what a one-column container
        // does — css-overflow-3 §2: content that does not fit simply
        // OVERFLOWS (paints below), and only `continue: discard` may drop
        // it. Engaging here would silently turn "painted below" into
        // "discarded", so the legacy path (which lets the column overflow)
        // owns N == 1. Note the used count collapses to 1 on narrow
        // containers too (resolveUsedColumns' gap fit), not only on an
        // explicit `column-count: 1`.
        // KNOWN CAVEAT (wave-43 lane V6): for a container that DOES declare
        // `continue: discard` the drop would be correct — this bail then
        // keeps the wrong (overflowing) render, because no discard flag is
        // threaded here. Left unbuilt deliberately: the corpus' complete
        // discard family (wave42-final discard-multicol-001…004) is all
        // ColumnCount 3, so an N == 1 discard branch would be unmeasurable
        // code. MulticolRunFragmentGateTest pins this tradeoff — retire its
        // caveat pin before threading a discard parameter through here.
        if (usedCount <= 1) {
            logFallback("run fragmentation: single used column — content overflows below instead of being clipped away")
            return null
        }
        // Specs must align 1:1 with measurables (a hoisted child that
        // composed nothing breaks the zip) — bail loudly.
        if (childSpecs.size != measurables.size) {
            logFallback("run fragmentation specs/measurables mismatch (${childSpecs.size} vs ${measurables.size})")
            return null
        }
        // Only plain flow children run-fragment: spanners/statics/forced
        // breaks each have an owning model (see the class doc).
        if (childSpecs.any { it.role != MulticolSpannerFlow.Role.FLOW }) {
            logFallback("run fragmentation: non-plain-flow child (spanner/static/forced-break) — legacy layout kept")
            return null
        }
        // The float bail — the honest wave-42 line for floats-clear-multicol
        // (see the class doc; the float lane owns the missing geometry).
        if (childSpecs.any { it.floatedContent }) {
            logFallback("run fragmentation: floated content — column fragmentation deferred to the float lane")
            return null
        }
        // The FORCED-BREAK bail: a `break-before/after: column` the role
        // list cannot express (the child's own break-before, or either side
        // on a descendant — MulticolSpannerFlow.ChildSpec.forcedBreakContent).
        // Measured evidence for why this MUST bail: WPT css-break
        // block-in-inline-013/014 are `columns:4; column-fill:auto;
        // height:400px` with four `<span>` children each wrapping a 100px
        // `break-before/after: column` div. The run model sees T = 400 = H,
        // concludes "fits one column", and would stack all four in column 0
        // — versus the greedy whole-child spread's one-per-column, which is
        // exactly Chromium's reference (both cells are green on Android at
        // 0.9967 in tools/titan/runs/wave41-final/sections/css-break).
        if (childSpecs.any { it.forcedBreakContent }) {
            logFallback("run fragmentation: forced column break inside a child — whole-child distribution kept (the run model cannot see it)")
            return null
        }
        // fill:balance keeps the whole-child greedy spread (green-cell
        // evidence in MulticolRunFragment's class doc).
        if (!columnFillAuto) {
            logFallback("run fragmentation: column-fill balance — whole-child distribution kept (breakpoint-faithful)")
            return null
        }
        // Measure every child ONCE at column width with unbounded block-
        // size: each lays out (and paints) at its natural height inside the
        // continuous strip — the slice source (same trick as wave-10).
        val placeables = measurables.map {
            it.measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = columnWidthPx,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity
                )
            )
        }
        // The pure run plan from MEASURED heights: strip offsets + slices
        // (empty slices when the run fits column 0 — §7.2 sequential fill).
        val plan = MulticolRunFragment.runPlan(
            childHeightsPx = placeables.map { it.height },
            columnBlockSizePx = columnBlockSizePx,
            columnWidthPx = columnWidthPx,
            columnGapPx = gapPx,
            columnCount = usedCount,
            // css-break-3 §4.1: an empty box has no breakpoint inside it,
            // so a column boundary must not pass through it — the plan
            // pushes such a child whole into the next column.
            monolithic = childSpecs.map { it.monolithicContent }
        )
        // The container stays exactly H tall (min==max==H under the
        // definite gate) and full width — like the wave-10 branch.
        return layout(constraints.maxWidth, columnBlockSizePx) {
            // Placement-phase publish (B-RC6 contract): placement is
            // skipped by intrinsic measurement and runs once per layout
            // pass; structuralEqualityPolicy swallows equal republishes.
            fragmentsBridge.value = plan.fragments
            // Place every child ONCE at its strip offset in column 0 —
            // every visible copy comes from the caller's replay slices.
            placeables.forEachIndexed { index, placeable ->
                placeable.place(0, plan.yOffsetsPx[index])
            }
        }
    }
}
