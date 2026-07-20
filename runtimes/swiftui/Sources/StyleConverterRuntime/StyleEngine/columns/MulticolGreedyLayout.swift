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

    /// Everything both protocol methods need, computed from ONE width so
    /// the measure and place passes can never drift (CSSFlexLayout's
    /// shared-plan pattern).
    private struct Plan {
        /// The §3 used column geometry (count >= 1, width >= 0).
        let used: MulticolMath.UsedColumns
        /// Per-child measured heights at the used column width.
        let heights: [Double]
        /// The greedy assignment — one slot per subview, index-aligned.
        let slots: [MulticolDistribution.Slot]
    }

    /// Build the distribution plan for a definite inline size.
    /// Mirrors Android's measure pass: resolve used columns from the
    /// available width, measure every child at the used column width
    /// with an unbounded block-size, then distribute greedily.
    private func plan(widthPx: CGFloat, subviews: Subviews) -> Plan {
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
        let heights = subviews.map {
            Double($0.sizeThatFits(ProposedViewSize(width: w, height: nil)).height)
        }
        // The shared greedy assignment (D-table-pinned on both platforms).
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
        let p = plan(widthPx: w, subviews: subviews)
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
        // size pass produced for this width.
        let p = plan(widthPx: bounds.width, subviews: subviews)
        // The used per-column inline size, shared by every slot.
        let w = CGFloat(p.used.widthPx)
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
