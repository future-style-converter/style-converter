//
//  MulticolGreedyLayout.swift
//  StyleEngine/columns — lane ios-multichild-multicol.
//
//  The multi-child multicol container as a SwiftUI custom Layout: the
//  iOS realization of Android's MultiColumnDistributionLayout greedy
//  path (pre-wave-10 machinery). ComponentRenderer routes a BLOCK
//  multicol container with 2+ in-flow children here instead of the
//  vertical stack, so whole children distribute across the used columns
//  exactly like Compose: css-multicol-1 §7.1 balancing APPROXIMATED by
//  Android's greedy min-height heuristic (the parity gate is "match
//  Android's algorithm", so the geometry composes the existing
//  machinery — MulticolMath used-columns for §3 fitting and
//  MulticolDistribution for the assignment, both pinned by the shared
//  D-table on the two platforms).
//
//  Single-child containers deliberately do NOT route here: the wave-9
//  column-box fill basis and the wave-10 fragmentation pass own that
//  family, and both stay byte-identical. A child that still overflows
//  its column inside a MULTI-child container renders unfragmented (the
//  container may overflow), mirroring Android's bail: fragmentation for
//  multi-child flow is out of contract on both platforms, and
//  ColumnsApplier.fragmentPlan's siblingCount guard already logs that
//  bail once (no silent fallthrough).
//

// SwiftUI for the Layout protocol; the arithmetic cores are view-free.
import SwiftUI

/// Greedy multi-child multicol container (iOS 16 Layout — same
/// availability discipline as CSSFlexLayout / CSSGridLayout).
@available(iOS 16.0, *)
struct MulticolGreedyLayout: Layout {
    /// Computed `column-count` (nil = auto) — feeds MulticolMath's §3 fit.
    let requestedCount: Int?
    /// Computed `column-width` in px (nil = auto) — the §3 width branch.
    let requestedWidthPx: Double?
    /// The USED column-gap in px — resolved by the caller through the
    /// same lane as the wave-9 fill basis (ComponentRenderer's
    /// multicolUsedGapPx: declared gap via GapApplier, else `normal` =
    /// 1em per css-align-3 §8.3), so slots and fill agree by construction.
    let gapPx: CGFloat
    /// Wave-21 lane MULTICOL: per-subview spanner-flow roles from
    /// MulticolSpannerFlow.rolesFor, in the exact order
    /// contentOrPlaceholder composes the subviews (leading text first).
    /// Nil — the default and every dark-stage caller (the renderer only
    /// passes roles under WPT capture) — keeps the greedy layout
    /// byte-identical; a roles list containing a spanner engages the
    /// css-multicol-1 §6 spanner-flow plan instead.
    var roles: [MulticolSpannerFlow.Role]? = nil
    /// Wave-43 lane V6 — css-overflow-4 §3 `continue: discard`, threaded
    /// from ColumnsConfig.continueDiscard through the renderer seam. Only
    /// the roles-gated spanner-flow branch consumes it (the plan marks
    /// content from the first §8.2 overflow column on as discarded); the
    /// greedy branch never reads it, so the default false — and every
    /// dark-stage caller — stays byte-identical to wave-42.
    var discardOverflow: Bool = false
    /// Wave-45 lane X3 — the FLOAT-STRIP seam (CSS 2.1 §9.5/§9.5.2
    /// inside css-multicol-1 column boxes, the wave-44 lane-U8 model):
    /// non-nil ONLY when the renderer's composition-time gate
    /// (MulticolFloatStrip.engagedStrip) proved the strip owns this
    /// container AND published the matching zero-flow plan on the
    /// floatClearancePlan environment — one decision, two consumers, so
    /// the floats this layout expects to measure as zero-flow really do
    /// (the wave-44 skeptic-S5 half-engage hazard). Nil — the default
    /// and every dark-stage caller — keeps every existing plan branch
    /// byte-identical.
    var floatStrip: MulticolFloatStrip.EngagedStrip? = nil

    /// Everything both protocol methods need, computed from ONE width so
    /// the measure and place passes can never drift (CSSFlexLayout's
    /// shared-plan pattern). Internal (not private) since wave 45: the
    /// float-strip branch lives in its own file
    /// (MulticolGreedyLayoutStrip.swift — the repo file-size rule) and
    /// Swift `private` is file-scoped.
    struct Plan {
        /// The §3 used column geometry (count >= 1, width >= 0).
        let used: MulticolMath.UsedColumns
        /// Per-child measured heights at the used column width.
        let heights: [Double]
        /// The greedy assignment — one slot per subview, index-aligned.
        let slots: [MulticolDistribution.Slot]
        /// Wave-21: the spanner-flow answer — non-nil ONLY when the roles
        /// gate engaged; the greedy `slots` above are then empty and every
        /// consumer reads this plan instead (§6.2 spanner sequencing +
        /// §6.3 balanced segments + css-position §3.1 static anchors).
        var spannerPlan: MulticolSpannerFlow.Plan? = nil
        /// Wave-45 lane X3: the float-strip answer — non-nil ONLY when
        /// the floatStrip seam engaged; `slots` are then empty and the
        /// placement maps each child's strip offset to its column via
        /// MulticolFloatStrip.columnSlot (§9.5.2 clearance offsets +
        /// fill/balance column block-size, the shared FS-table geometry).
        var stripPlan: MulticolFloatStrip.StripPlan? = nil
    }

    /// Build the distribution plan for a definite inline size.
    /// Mirrors Android's measure pass: resolve used columns from the
    /// available width, measure every child at the used column width
    /// with an unbounded block-size, then distribute greedily.
    /// `heightPx` (wave-45 lane X3) is the definite column block-size H
    /// for the float-strip branch — the PROPOSED height in sizeThatFits
    /// and bounds.height in placeSubviews, i.e. the container's actual
    /// content block-size, the same live-constraints basis the Compose
    /// strip gates on (its min==max height constraints); nil (the ideal
    /// probe, or an auto-height chain) declines the strip only.
    private func plan(widthPx: CGFloat, heightPx: CGFloat?, subviews: Subviews) -> Plan {
        // §3 used-value fit — the SAME math (MulticolMath) the wave-9
        // fill basis and the wave-10 fragment plan consume.
        let used: MulticolMath.UsedColumns
        if let u = MulticolMath.usedColumns(availableWidthPx: Double(widthPx),
                                            requestedCount: requestedCount,
                                            requestedWidthPx: requestedWidthPx,
                                            gapPx: Double(gapPx)) {
            // The normal path — the renderer's gate guarantees a multicol
            // container (count or width non-auto), so this always fires.
            used = u
        } else {
            // Unreachable via the renderer gate (isMulticolContainer
            // implies one non-auto input) — but a Layout must answer.
            // No-silent-fallthrough: say so once, then behave like the
            // pre-lane vertical stack (one full-width column).
            PropertyTracker.logOnce(
                key: "multicol-distribute-no-columns",
                message: "multicol distribution: both column-count and "
                    + "column-width auto — children render as one column")
            used = MulticolMath.UsedColumns(count: 1,
                                            widthPx: Double(max(0, widthPx)))
        }
        // Android measures each child with maxWidth = column width and an
        // unbounded height; the SwiftUI analogue proposes (W, nil) — a
        // child with an explicit width keeps it, auto content wraps at W.
        let w = CGFloat(used.widthPx)
        // ── Wave-45 lane X3: the FLOAT-STRIP branch (wave-44 U8 model) ──
        // Engages only when the renderer seam threaded an EngagedStrip;
        // the whole branch — gates, zero-flow measure, FS geometry —
        // lives in MulticolGreedyLayoutStrip.swift (the repo file-size
        // rule). The strip owns all-flow containers, the spanner branch
        // below owns containers WITH a spanner/forced break — disjoint
        // by role, so branch order cannot flip a result.
        if let sp = stripPlan(used: used, columnWidth: w,
                              heightPx: heightPx, subviews: subviews) {
            return sp
        }
        // ── Wave-21 spanner-flow branch (css-multicol-1 §6) ─────────────
        // Engages only when the renderer supplied ALIGNED roles containing
        // a spanner (capture-gated at the call site). Spanners measure at
        // the FULL container width (§6.2 full-width span); flow children
        // keep the column-width proposal. Everything else — nil roles,
        // spanner-free role lists — keeps the greedy path byte-identical.
        // Wave-42 lane W4: a FORCED column break (break-after: column,
        // css-break-3 §4.1) also takes the plan — the greedy min-height
        // heuristic cannot honor it, and for the ≤N one-child-per-chunk
        // shapes the corpus's green cells exercise the chunk walk is
        // provably greedy-identical (BRK4 pin), so engaging is
        // render-neutral there and correct everywhere else.
        if let roles, roles.contains(where: { $0 == .spanner || $0 == .flowBreakAfter }) {
            // Roles must align 1:1 with subviews; a mismatch means some
            // child composed differently than the renderer predicted —
            // bail loudly to greedy (repo no-silent-fallthrough rule).
            guard roles.count == subviews.count else {
                PropertyTracker.logOnce(
                    key: "multicol-spanner-roles-mismatch",
                    message: "multicol spanner flow: roles/subviews mismatch "
                        + "(\(roles.count) vs \(subviews.count)) — greedy layout kept")
                return greedyPlan(used: used, columnWidth: w, subviews: subviews)
            }
            // Per-role measure: spanner at container width, flow at W.
            let heights = zip(roles, subviews).map { role, sub in
                Double(sub.sizeThatFits(ProposedViewSize(
                    width: role == .spanner ? widthPx : w, height: nil)).height)
            }
            // The shared SP-table plan (pinned identically on Android).
            // Wave-43 lane V6: `continue: discard` rides into the plan —
            // css-overflow-4 §3 drops content from the first overflow
            // column on (discard-multicol-003's 4th chunk AND the spanner
            // after it), exactly as Compose already threads it
            // (MultiColumnApplier → MulticolSpannerFlow.plan).
            let sp = MulticolSpannerFlow.plan(
                children: zip(heights, roles).map { MulticolSpannerFlow.Child(heightPx: $0, role: $1) },
                columnCount: used.count,
                discardOverflow: discardOverflow)
            // Whole-child placement approximation: say so once when a
            // child straddles a balanced boundary (no fragmentation for
            // multi-child segments — mirrors Android's log).
            if MulticolSpannerFlow.anyFlowChildCrossesBoundary(
                children: zip(heights, roles).map { MulticolSpannerFlow.Child(heightPx: $0, role: $1) },
                columnCount: used.count) {
                PropertyTracker.logOnce(
                    key: "multicol-spanner-segment-fragmentation",
                    message: "multicol spanner flow: segment fragmentation "
                        + "(multi-child) not implemented — children place whole")
            }
            return Plan(used: used, heights: heights, slots: [], spannerPlan: sp)
        }
        return greedyPlan(used: used, columnWidth: w, subviews: subviews)
    }

    /// The pre-wave-21 greedy plan, factored so the spanner branch's
    /// mismatch bail and the legacy path share one implementation.
    private func greedyPlan(used: MulticolMath.UsedColumns,
                            columnWidth w: CGFloat,
                            subviews: Subviews) -> Plan {
        // Byte-identical to the original inline body: measure at W…
        let heights = subviews.map {
            Double($0.sizeThatFits(ProposedViewSize(width: w, height: nil)).height)
        }
        // …then the shared greedy assignment (D-table-pinned on both platforms).
        let slots = MulticolDistribution.distribute(childHeightsPx: heights,
                                                    columnCount: used.count)
        return Plan(used: used, heights: heights, slots: slots)
    }

    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        // No children → no columns worth reporting (matches the empty
        // Android layout: height 0; width is moot for an empty box).
        guard !subviews.isEmpty else { return .zero }
        // A definite inline proposal drives the real plan — Android's
        // counterpart always measures under a bounded maxWidth
        // (Modifier.fillMaxWidth inside the bounded harness canvas).
        guard let w = proposal.width, w.isFinite else {
            // SwiftUI's ideal-size probe (.unspecified) has no Android
            // analogue; answer with the single-column stack of ideal
            // sizes (the pre-lane VStack shape). Every fixture in the
            // family declares a container width, so the definite branch
            // decides the actual layout.
            let ideals = subviews.map { $0.sizeThatFits(.unspecified) }
            // Widest child wide, children stacked flush tall.
            return CGSize(width: ideals.map(\.width).max() ?? 0,
                          height: ideals.map(\.height).reduce(0, +))
        }
        // Plan once from the proposed width (placeSubviews re-derives the
        // identical plan from bounds.width — pure inputs cannot drift).
        let p = plan(widthPx: w, heightPx: proposal.height, subviews: subviews)
        // Wave-45 lane X3: an engaged strip keeps the container at its
        // definite block-size H — the proposed height that fed the plan
        // (Compose parity: the strip layout reports the DECLARED H even
        // when §7.1 balance shortens the columns; the balancing refs
        // keep the 100px box with 85px columns).
        if p.stripPlan != nil, let h = proposal.height {
            return CGSize(width: w, height: h)
        }
        // Wave-21: a spanner-flow plan owns the container block-size —
        // §6.3 balanced segments + spanner heights (SP-table).
        if let sp = p.spannerPlan {
            return CGSize(width: w, height: sp.containerBlockSizePx)
        }
        // Android reports (constraints.maxWidth, tallest column) — the
        // container spans the full available inline size and is exactly
        // as tall as its lowest-reaching child.
        return CGSize(width: w,
                      height: MulticolDistribution.containerBlockSizePx(
                        childHeightsPx: p.heights, slots: p.slots))
    }

    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout ()) {
        // Nothing to place for an empty container.
        guard !subviews.isEmpty else { return }
        // bounds.width is always definite here — derive the same plan the
        // size pass produced for this width (bounds.height IS the size
        // pass's answer, so the strip branch re-derives identically).
        let p = plan(widthPx: bounds.width, heightPx: bounds.height, subviews: subviews)
        // The used per-column inline size, shared by every slot.
        let w = CGFloat(p.used.widthPx)
        // ── Wave-45 lane X3: float-strip placement ──────────────────────
        // Each child places ONCE in the column its strip offset starts
        // in — the body lives beside the plan branch in
        // MulticolGreedyLayoutStrip.swift (one file owns the strip).
        if let sp = p.stripPlan {
            placeStripSubviews(sp, in: bounds, used: p.used,
                               columnWidth: w, subviews: subviews)
            return
        }
        // ── Wave-21 spanner-flow placement (css-multicol-1 §6) ──────────
        if let sp = p.spannerPlan {
            // One slot per subview, index-aligned by construction.
            for (index, sub) in subviews.enumerated() {
                let slot = sp.slots[index]
                // Wave-42: css-overflow-4 §3 DISCARDED slots render nothing
                // — but a SwiftUI Layout must place every subview (an
                // unplaced subview gets a DEFAULT placement, which would
                // paint it mid-container), so park them far offscreen.
                if slot.discarded {
                    sub.place(at: CGPoint(x: bounds.maxX + 1_000_000,
                                          y: bounds.maxY + 1_000_000),
                              anchor: .topLeading,
                              proposal: ProposedViewSize(width: w, height: nil))
                    continue
                }
                // Spanners span all columns: x=0, full container width
                // (§6.2); flow/static children sit at their column's
                // inline origin i·(W+G) — the Android placement twin.
                let x = slot.role == .spanner
                    ? bounds.minX
                    : bounds.minX + CGFloat(slot.columnIndex) * (w + gapPx)
                // The SP-table block offset from the content-box top.
                sub.place(at: CGPoint(x: x, y: bounds.minY + CGFloat(slot.yPx)),
                          anchor: .topLeading,
                          // Spanner proposes the full width; columns W.
                          proposal: ProposedViewSize(
                            width: slot.role == .spanner ? bounds.width : w,
                            height: nil))
            }
            return
        }
        // Place in child order (geometry is disjoint by construction, so
        // order does not change the paint result vs Android's
        // column-major placement — see MultiColumnApplier's layout loop).
        for (index, sub) in subviews.enumerated() {
            // This child's greedy assignment.
            let slot = p.slots[index]
            // Inline position: column i starts at i · (W + G) from the
            // content-box origin — PHYSICAL left-to-right, exactly like
            // Android's `columnIndex * (columnWidth + gapPx)` x math.
            let x = bounds.minX + CGFloat(slot.columnIndex) * (w + gapPx)
            // Block position: the column's running height before this
            // child (flush stacking — no inter-item gap, like Compose).
            let y = bounds.minY + CGFloat(slot.yOffsetPx)
            // topLeading anchor: a child narrower than W sits at the
            // column's start edge, matching Android's place(x, y).
            sub.place(at: CGPoint(x: x, y: y), anchor: .topLeading,
                      // Same proposal as the measure pass so the child
                      // lays out at the size the plan measured.
                      proposal: ProposedViewSize(width: w, height: nil))
        }
    }
}
