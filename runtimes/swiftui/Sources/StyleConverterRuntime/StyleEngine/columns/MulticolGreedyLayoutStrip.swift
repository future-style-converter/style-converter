//
//  MulticolGreedyLayoutStrip.swift
//  StyleEngine/columns — wave-45 lane X3 (the layout half).
//
//  MulticolGreedyLayout's FLOAT-STRIP branch (the wave-44 U8 model:
//  CSS 2.1 §9.5 out-of-flow floats + §9.5.2 clearance inside
//  css-multicol-1 column boxes), split into its own file by the repo
//  file-size rule — the host file already carries the greedy + spanner
//  machinery. One file owns the strip: the plan branch AND its
//  placement twin live here so the two can never drift.
//
//  Engagement is decided at COMPOSITION time by the renderer's X3 seam
//  (MulticolFloatStripSeam.engagedStrip), which also publishes the
//  matching §9.5.2 zero-flow plan on the floatClearancePlan environment
//  — one decision, two consumers, so the floats this branch measures as
//  zero-flow really are (the wave-44 skeptic-S5 half-engage hazard).
//

// SwiftUI for the Layout protocol types (Subviews, ProposedViewSize).
import SwiftUI

@available(iOS 16.0, *)
extension MulticolGreedyLayout {

    /// The strip plan for an engaged container, or nil to fall through
    /// to the spanner/greedy branches. Unlike the Compose measure half
    /// (one measure per Measurable per pass — declines must land
    /// pre-measure or crash), SwiftUI subview proxies may be measured
    /// repeatedly, so a late decline here safely falls through — every
    /// reachable decline is logged, never silent.
    func stripPlan(used: MulticolMath.UsedColumns,
                   columnWidth w: CGFloat,
                   heightPx: CGFloat?,
                   subviews: Subviews) -> Plan? {
        // Not engaged (dark stage and every non-strip container).
        guard let strip = floatStrip else { return nil }
        // H from the LIVE pass — the proposed height in sizeThatFits /
        // bounds.height in placeSubviews, the same live-constraints
        // basis the Compose strip gates on (min==max constraints). The
        // ideal probe (nil/infinite) has no fragmentainer to slice
        // with; the composition gate required a definite block-size, so
        // the REAL pass always carries one — declining the probe
        // silently is correct, not a fallthrough.
        guard let hRaw = heightPx, hRaw.isFinite, hRaw > 0 else { return nil }
        // Specs must align 1:1 with subviews — a child that composed
        // differently than the renderer predicted would desync every
        // strip offset (the Compose specs/measurables residual, the
        // same loud bail).
        guard strip.specs.count == subviews.count else {
            PropertyTracker.logOnce(
                key: "multicol-float-strip-specs-mismatch",
                message: "multicol float strip: specs/subviews mismatch "
                    + "(\(strip.specs.count) vs \(subviews.count)) — greedy layout kept")
            return nil
        }
        // N == 1: one column overflows below instead of slicing
        // (css-overflow-3 §2 — the run-twin containment rationale).
        guard used.count > 1 else {
            PropertyTracker.logOnce(
                key: "multicol-float-strip-single-column",
                message: "multicol float strip: single used column — "
                    + "content overflows below instead of fragmenting")
            return nil
        }
        // Zero-flow measured heights: the renderer published the
        // matching §9.5.2 plan around this content, so a float
        // container's floats report zero height and the measured value
        // IS the child's in-flow strip extent.
        let heights = subviews.map {
            Double($0.sizeThatFits(ProposedViewSize(width: w, height: nil)).height)
        }
        // The shared FS-table geometry: §9.5.2 clearance offsets +
        // per-side float ledgers + the fill/balance used column
        // block-size (Kotlin twin: the FS-C rows).
        guard let sp = MulticolFloatStrip.plan(
            specs: strip.specs,
            measuredHeightsPx: heights,
            columnBlockSize: Double(hRaw),
            columnFillAuto: strip.columnFillAuto,
            columnWidth: used.widthPx,
            gapPx: Double(gapPx),
            columnCount: used.count) else {
            // Geometry declined (the one reachable decline: a degenerate
            // balanced strip) — fall through to greedy, logged.
            PropertyTracker.logOnce(
                key: "multicol-float-strip-declined",
                message: "multicol float strip: geometry declined "
                    + "(degenerate balanced strip) — greedy layout kept")
            return nil
        }
        // Seam 2 (the css-break-3 §4 slice replay) is not built on iOS —
        // float ink taller than a column stays in its anchor column. Say
        // so once (repo no-silent-fallthrough rule; the clone-row seam
        // is renderer-owned — MulticolFloatStripSeam's banner records
        // the ownership split).
        PropertyTracker.logOnce(
            key: "multicol-float-strip-no-replay",
            message: "multicol float strip: children place at their "
                + "strip columns, but the css-break-3 §4 slice replay "
                + "(clone-row seam) is not implemented — float ink "
                + "stays in its anchor column")
        return Plan(used: used, heights: heights, slots: [], stripPlan: sp)
    }

    /// The strip placement twin: each child places ONCE in the column
    /// its strip offset starts in (columnSlot's ⌊y/h⌋ + local remainder
    /// — h is the FILL or BALANCED used column block-size from the
    /// plan, not the container's declared H). The floats inside the
    /// float container paint at their zero-flow slots (the renderer-
    /// published §9.5.2 plan), so the cleared box lands exactly at the
    /// ref's clearance position; ink taller than one column stays in
    /// its anchor column (the seam-2 approximation logged above).
    func placeStripSubviews(_ sp: MulticolFloatStrip.StripPlan,
                            in bounds: CGRect,
                            used: MulticolMath.UsedColumns,
                            columnWidth w: CGFloat,
                            subviews: Subviews) {
        for (index, sub) in subviews.enumerated() {
            // The pure column mapping (FS-Z4 rows pin the fixture
            // positions: strip 250 → column 2 local 50 under fill,
            // local 80 under balance).
            let slot = MulticolFloatStrip.columnSlot(
                yOffsetPx: sp.yOffsetsPx[index],
                columnBlockSizePx: sp.columnBlockSizePx,
                columnCount: used.count)
            sub.place(
                // Column i's inline origin — the same i·(W+G) math as
                // every other placement branch in this layout.
                at: CGPoint(x: bounds.minX + CGFloat(slot.column) * (w + gapPx),
                            y: bounds.minY + CGFloat(slot.localYPx)),
                anchor: .topLeading,
                // Same proposal as the measure pass so the child lays
                // out at the size the plan measured.
                proposal: ProposedViewSize(width: w, height: nil))
        }
    }
}
