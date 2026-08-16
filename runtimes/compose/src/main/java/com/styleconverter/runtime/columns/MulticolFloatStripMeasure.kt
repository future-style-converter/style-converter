// The Compose half of the wave-44 lane-U8 float-strip fragmentation: the
// measure-pass integration MultiColumnDistributionLayout delegates to for
// definite-height multicol containers whose flow children carry proven
// leading floats (MulticolFloatStrip owns classification,
// MulticolFloatStripPlan the geometry). Split out of MultiColumnApplier.kt
// exactly like MulticolRunFragmentMeasure so the B-RC6 bridge pin —
// MultiColumnApplier.kt touches fragmentsState.value exactly 3 times —
// stays intact: THIS file's placement block owns the strip branch's write.
package com.styleconverter.runtime.columns

// The measure→draw snapshot bridge type — written ONLY in the placement
// block below (the wedge lane's B-RC6 contract, same as the run twin).
import androidx.compose.runtime.MutableState
// The Layout-pass types this integration bridges between the pure strip
// plan and Compose: measurables in, placement commands out.
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
// Constraints.Infinity marks the unbounded child measure — each child lays
// out (and paints) at its natural block-size inside the continuous strip.
import androidx.compose.ui.unit.Constraints

/**
 * Measures + places a definite-height multicol container's children as ONE
 * continuous block strip with §9.5 out-of-flow floats and §9.5.2 clearance,
 * and publishes the css-break-3 §4 slices for the caller's drawWithContent
 * clip+translate replay. The paint half of the model — floats reporting
 * zero flow height while painting at their float positions — is the
 * synthetic zero-flow plan [MulticolFloatStrip.zeroFlowPlan] that
 * MultiColumnLayout provides around the SAME content this pass measures,
 * so the measured heights here are the in-flow extents by construction.
 *
 * Returns null whenever the strip does NOT engage — the caller then falls
 * through to its legacy paths byte-identically (capture gating is inherent:
 * childSpecs is nulled outside WPT capture at the MultiColumnLayout
 * boundary). EVERY such null is decided BEFORE the measure loop: the
 * caller's fallback path re-measures the same measurables, which Compose
 * forbids within one layout pass, so a late decline would throw instead of
 * falling back (wave-44 skeptic S5 — the invariant is spelled out at the
 * last gate below and pinned by MulticolFloatStripTest's FS-E rows).
 * Unlike the run twin this pass takes BOTH fill modes: §7.2 sequential fill
 * (auto) and the §7.1 balanced reduction (balance) — the balancing refs pin
 * H = ceil(255/3) = 85, see MulticolFloatStripPlan.
 */
internal object MulticolFloatStripMeasure {

    fun MeasureScope.measureFloatStrip(
        // The Layout's measurables, in RenderContent order.
        measurables: List<Measurable>,
        // The Layout's incoming constraints (post Box loosening).
        constraints: Constraints,
        // Per-measurable role/float-strip specs (null outside capture —
        // inherent dark-stage protection, same convention as the twins).
        childSpecs: List<MulticolSpannerFlow.ChildSpec>?,
        // True iff the container declares `column-fill: auto` (§7.2);
        // false = the initial `balance` (§7.1 reduction).
        columnFillAuto: Boolean,
        // False under vertical writing modes — the strip's block-axis
        // stacking and −i·H replay would slice along the wrong axis
        // (css-writing-modes-4 §3; the wave-42 skeptic's S2 lesson).
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
        // legacy path, no log (not a fallthrough, same as the twins).
        if (childSpecs == null) return null
        // Shape absent (no proven floats / an unprovable child / non-flow
        // roles) → the run/greedy paths own the container — also not a
        // fallthrough: this model simply does not apply.
        if (!MulticolFloatStrip.engages(childSpecs)) return null
        // Vertical writing-mode bail (blocked-platform) — logged because
        // the shape DID match and the render stays the stacked-float
        // legacy layout (repo no-silent-fallthrough rule).
        if (!fragmentationAllowed) {
            logFallback("float strip: vertical writing-mode (blocked-platform)")
            return null
        }
        // N == 1: one column overflows below instead of slicing — the run
        // twin's exact containment rationale (css-overflow-3 §2).
        if (usedCount <= 1) {
            logFallback("float strip: single used column — content overflows below instead of fragmenting")
            return null
        }
        // Specs must align 1:1 with measurables (a hoisted child that
        // composed nothing breaks the zip) — bail loudly.
        if (childSpecs.size != measurables.size) {
            logFallback("float strip specs/measurables mismatch (${childSpecs.size} vs ${measurables.size})")
            return null
        }
        // H must be a real fragmentainer (css-break-3 §2). The plan re-gates
        // on this too; it is checked HERE so the answer lands before the
        // measure loop below (see the pre-measure invariant note).
        if (columnBlockSizePx <= 0) {
            logFallback("float strip: non-positive definite block-size — no fragmentainer, legacy layout kept")
            return null
        }
        // ── THE PRE-MEASURE INVARIANT (wave-44 skeptic S5, D1) ───────────
        // Every decline of this pass must happen BEFORE the measure loop
        // below, and the reason is not tidiness: returning null hands the
        // container back to MultiColumnApplier's legacy loop, which
        // measures the SAME measurables again — Compose allows exactly one
        // measure per Measurable per layout pass and throws
        // IllegalStateException on the second. A decline taken after the
        // loop would therefore CRASH the capture, never "keep the legacy
        // layout" (which is what the old post-measure log claimed).
        // The one plan decline that needed measured heights — §7.1 balance
        // with C == 0 — is answered from the IR here instead, by the SAME
        // predicate the composition-side zero-flow gate calls, so paint and
        // layout can never half-engage.
        if (!MulticolFloatStripPlan.engagesPreMeasure(childSpecs, columnFillAuto)) {
            logFallback(
                "float strip: column-fill balance with no IR-provable ink " +
                    "(every float height 0, no trailing band) — legacy layout kept"
            )
            return null
        }
        // Measure every child ONCE at column width with unbounded block-
        // size: floats inside report zero flow height (the zero-flow plan
        // wrapped around this same content), so each measured height IS
        // the child's in-flow strip extent — the slice source.
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
        // The pure strip plan: §9.5.2 clearance offsets + float ledgers +
        // the fill/balance column block-size + the replay slices.
        val plan = MulticolFloatStripPlan.plan(
            specs = childSpecs,
            measuredHeightsPx = placeables.map { it.height },
            columnBlockSizePx = columnBlockSizePx,
            columnFillAuto = columnFillAuto,
            columnWidthPx = columnWidthPx,
            columnGapPx = gapPx,
            columnCount = usedCount
        )
        if (plan == null) {
            // UNREACHABLE with the gates above: engagement, 1:1 specs, H > 0,
            // N > 1 and a provable ink floor are exactly the plan's four
            // decline conditions, all answered pre-measure. Kept total on
            // purpose — the children are already measured at this point, so
            // handing the container back to the caller's legacy loop is the
            // one thing this branch may NOT do (that loop re-measures the
            // same measurables and Compose throws). If a future plan edit
            // adds a fifth decline, the honest degraded render is the
            // degenerate strip the only reachable decline (C == 0)
            // describes: a flush stack of the measured children with no
            // slices — logged, never silent.
            logFallback(
                "float strip: geometry declined AFTER measure — flush-stacked " +
                    "the measured children (re-entering the legacy loop here " +
                    "would measure them twice)"
            )
            return layout(constraints.maxWidth, columnBlockSizePx) {
                // Placement-phase publish, same B-RC6 contract as below: no
                // slices, so the caller's drawWithContent replays plainly.
                fragmentsBridge.value = emptyList()
                // CSS 2.1 §9.4.1 flush stacking — the strip walk minus the
                // float ledgers this branch has no plan for.
                var y = 0
                placeables.forEach { p ->
                    p.place(0, y)
                    y += p.height
                }
            }
        }
        // The container stays exactly H tall (min==max==H under the
        // definite gate) and full width — like the wave-10/42 branches;
        // the balanced strip (h < H) still reports the DECLARED height
        // (the balancing refs keep the 100px box with 85px columns).
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
