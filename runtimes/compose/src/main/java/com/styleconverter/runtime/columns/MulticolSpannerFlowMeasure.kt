// The Compose half of the wave-21 spanner-flow plan: the measure-pass
// integration that MultiColumnDistributionLayout delegates to. Split out of
// MultiColumnApplier.kt (already far past the 200-line target) so the new
// machinery stays reviewable next to the pure math it consumes.
package com.styleconverter.runtime.columns

// The measure→draw snapshot bridge type — the write happens ONLY in this
// helper's placement block (the wedge lane's B-RC6 contract; the bridge
// test pins MultiColumnApplier.kt to its own three touches, so the state
// OBJECT is handed over instead of a write lambda).
import androidx.compose.runtime.MutableState
// The three Layout-pass types this integration bridges between the pure
// plan and Compose: measurables in, placement commands out.
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
// Constraints.Infinity marks the unbounded child measure (the continuous
// slice source of the wave-10 replay — same trick as the definite branch).
import androidx.compose.ui.unit.Constraints

/**
 * Measures + places a multicol container per [MulticolSpannerFlow.plan]
 * (css-multicol-1 §6.2–§6.3 spanner sequencing, §7.1 auto-height
 * balancing, css-position-3 §3.1 abspos static anchors).
 *
 * Returns null whenever the plan does NOT engage — the caller then falls
 * through to its legacy paths byte-identically (greedy distribution, or
 * the wave-10 definite-height fragmentation). Engagement requires:
 *  - per-measurable roles supplied AND aligned (a mismatch — e.g. a
 *    hoisted child that composed nothing — logs once and bails);
 *  - horizontal-tb (vertical writing modes are blocked-platform);
 *  - a spanner present, or an auto-height sole-flow-child container
 *    (see [MulticolSpannerFlow.engages]; definite-height spannerless
 *    containers keep the wave-10 sequential-fill machinery).
 *
 * Drawing: when [MulticolSpannerFlow.soleFlowFragmentReplay] allows it,
 * the sole flow child is measured ONCE unbounded and its fragments are
 * published for the caller's drawWithContent clip+translate replay
 * (wave-10 pass, geometry from [FragmentGeometry] with the BALANCED
 * column block-size as the fragmentainer). Otherwise children place
 * whole from their start column — an approximation when a child crosses
 * a balanced boundary, logged once via [logFallback] (repo
 * no-silent-fallthrough rule).
 */
internal object MulticolSpannerFlowMeasure {

    fun MeasureScope.measureSpannerFlow(
        // The Layout's measurables, in RenderContent order.
        measurables: List<Measurable>,
        // The Layout's incoming constraints (post Box loosening).
        constraints: Constraints,
        // Per-measurable role + declared-block-size specs from
        // MulticolSpannerFlow.specsFor — null means the caller has no
        // child information (legacy callers).
        childSpecs: List<MulticolSpannerFlow.ChildSpec>?,
        // §3.4 used column count (resolveUsedColumns — always >= 1).
        usedCount: Int,
        // §3.4 used per-column width in px (always >= 0).
        columnWidthPx: Int,
        // Used column-gap in px.
        gapPx: Int,
        // css-break-3 §2: whether a definite fragmentainer exists — the
        // wave-10 machinery owns definite-height SPANNERLESS containers.
        definiteBlockSize: Boolean,
        // False under vertical writing modes (blocked-platform bail).
        fragmentationAllowed: Boolean,
        // The measure→draw bridge state ([] = plain drawContent). Written
        // ONLY inside the placement block below (B-RC6 contract).
        fragmentsBridge: MutableState<List<FragmentGeometry.Fragment>>,
        // One-shot fallback logger (MultiColumnApplier's shared dedupe).
        logFallback: (String) -> Unit
    ): MeasureResult? {
        // No specs → the caller had no IR child view (dark stage passes
        // null by construction) — legacy path, no log (not a fallthrough).
        if (childSpecs == null) return null
        // Convenience view: the role list drives every branch below.
        val roles = childSpecs.map { it.role }
        // B-RC6 interop: an UNBOUNDED inline size has no §3.4 used column
        // geometry (the half-Infinity width is unrepresentable in child
        // Constraints — see the wedge lane's inlineSizeBounded gate in
        // MultiColumnDistributionLayout); the caller's degenerate
        // one-column path owns that shape.
        if (constraints.maxWidth == Constraints.Infinity) return null
        // Roles must align 1:1 with measurables; a mismatch means some
        // child composed nothing (wave-17 hoist) or extra content exists.
        // Bail loudly — silently mis-assigning roles would be worse.
        if (roles.size != measurables.size) {
            logFallback("spanner-flow roles/measurables mismatch (${roles.size} vs ${measurables.size})")
            return null
        }
        val hasSpanner = roles.any { it == MulticolSpannerFlow.Role.SPANNER }
        // Definite-height spannerless containers: the wave-10 sequential
        // fragmentation (column-fill:auto semantics) stays authoritative.
        if (!hasSpanner && definiteBlockSize) return null
        // Vertical writing modes: the plan's inline/block axes would both
        // be wrong — same blocked-platform bail as the wave-10 pass.
        if (hasSpanner && !fragmentationAllowed) {
            logFallback("spanner flow under vertical writing-mode (blocked-platform)")
            return null
        }
        if (!fragmentationAllowed) return null
        // Non-destructive probe (a measurable may still be measured after
        // an intrinsic query): natural heights feed the engagement gate.
        // Spanners probe at container width (§6.2 full-width), everything
        // else at the used column width (§2 column containing block).
        val probed = measurables.mapIndexed { i, m ->
            m.minIntrinsicHeight(
                if (roles[i] == MulticolSpannerFlow.Role.SPANNER) constraints.maxWidth
                else columnWidthPx
            )
        }
        val probeChildren = roles.indices.map {
            MulticolSpannerFlow.Child(probed[it], roles[it], childSpecs[it].explicitBlockSize)
        }
        // The narrow engagement gate (SP-table pinned) — false keeps every
        // legacy fixture byte-identical.
        if (!MulticolSpannerFlow.engages(probeChildren, usedCount)) return null
        // Real measure, one pass: spanners at full container width, flow
        // and static children at column width; block-size unbounded so an
        // over-tall flow child lays out as the continuous slice source.
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = if (roles[i] == MulticolSpannerFlow.Role.SPANNER) constraints.maxWidth
                    else columnWidthPx,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity
                )
            )
        }
        // Plan from MEASURED heights (the probe only gated entry) — the
        // same discipline as the wave-10 branch's measured-C geometry.
        val children = roles.indices.map {
            MulticolSpannerFlow.Child(placeables[it].height, roles[it], childSpecs[it].explicitBlockSize)
        }
        val plan = MulticolSpannerFlow.plan(children, usedCount)
        // The replay fragment list this pass wants drawn — computed HERE,
        // but published only inside the placement block below (the wedge
        // lane's B-RC6 contract: a measure-body snapshot write executes
        // during intrinsic passes and registers a measure-pass read,
        // ping-ponging nested multicol into remeasure loops).
        val fragments: List<FragmentGeometry.Fragment>
        if (MulticolSpannerFlow.soleFlowFragmentReplay(children)) {
            // Sole-flow replay: fragment the one flow child with the
            // BALANCED H as fragmentainer (its slot is provably (0,0) —
            // all spanners are 0-height under the replay gate).
            val flowIndex = roles.indexOfFirst { it == MulticolSpannerFlow.Role.FLOW }
            val h = plan.soleFlowColumnBlockSizePx ?: 0
            fragments = if (h > 0) FragmentGeometry.fragmentGeometry(
                childBlockSizePx = placeables[flowIndex].height,
                columnBlockSizePx = h,
                columnWidthPx = columnWidthPx,
                columnGapPx = gapPx,
                columnCount = usedCount
            ) else emptyList()
        } else {
            // Whole-child placement — no replay (empty list also clears
            // any stale fragments from a previous size).
            fragments = emptyList()
            // A child straddling a balanced boundary renders unfragmented
            // from its start column: honest approximation, said once.
            if (MulticolSpannerFlow.anyFlowChildCrossesBoundary(children, usedCount)) {
                logFallback("spanner-flow segment fragmentation (multi-child) — children place whole")
            }
        }
        // The container: full available inline size (like the legacy
        // paths), auto block-size from the plan (§6.3 balanced segments
        // + spanner heights).
        return layout(constraints.maxWidth, plan.containerBlockSizePx) {
            // Placement-phase publish (B-RC6 contract, see above):
            // placement is skipped by intrinsic measurement and runs once
            // per layout pass; mutableStateOf's structuralEqualityPolicy
            // swallows equal-value republishes.
            fragmentsBridge.value = fragments
            // In-flow content first — spanners at x=0 (full width), flow
            // children at their column's inline origin i·(W+G).
            plan.slots.forEachIndexed { index, slot ->
                if (slot.role != MulticolSpannerFlow.Role.STATIC) {
                    val x = if (slot.role == MulticolSpannerFlow.Role.SPANNER) 0
                    else slot.columnIndex * (columnWidthPx + gapPx)
                    placeables[index].place(x, slot.yPx)
                }
            }
            // Statics LAST: CSS 2.1 Appendix E — positioned descendants
            // paint after in-flow content, so the abspos ink (which the
            // wave-18 zeroFlowAnchor paints at its placed origin) covers
            // the flow content at the same static position.
            plan.slots.forEachIndexed { index, slot ->
                if (slot.role == MulticolSpannerFlow.Role.STATIC) {
                    placeables[index].place(slot.columnIndex * (columnWidthPx + gapPx), slot.yPx)
                }
            }
        }
    }
}
