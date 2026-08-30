//
//  MulticolFloatStripSlice.swift
//  StyleEngine/columns — wave-48 lane W3 (the measure half + slice replay).
//
//  The iOS MEASURE HALF of the wave-44 float-strip model — the mirror of
//  Compose's MulticolFloatStripMeasure — realized the way iOS fragments
//  everything (MulticolRunFragment's banner): the RENDERER composes one
//  clone of the whole strip content per column (the multicolFragmentRow
//  precedent), and each clone sits inside THIS layout, which measures the
//  children under the seam's §9.5.2 zero-flow plan, builds the shared FS
//  geometry (MulticolFloatStrip.plan — the Kotlin-twin math), and places
//  the clone shifted by −band·h so the outer `.clipped()` window shows
//  exactly the css-break-3 §4 slice [band·h, (band+1)·h).  This closes
//  seam 2 of the old CONSUMPTION STATUS banner: before wave 48 each child
//  placed ONCE in its anchor column and float ink taller than one column
//  overflowed below it (wave48-cal iOS: aqua rows 111–360 vs the refs'
//  sliced 111–210 on all 8 floats-clear-multicol cells).
//
//  The Compose PRE-MEASURE DECLINE DISCIPLINE (wave-44 skeptic S5, D1)
//  maps onto SwiftUI like this: Compose must decline BEFORE measuring
//  because its fallback re-measures the same measurables and a second
//  measure throws; SwiftUI proxies may be measured repeatedly, so there is
//  no crash class — but the COMMITMENT class is the same: once the
//  renderer composed the slice row there is no other layout to fall back
//  to.  Every real decline therefore lives at COMPOSITION time in the ONE
//  shared predicate both halves gate on
//  (MulticolFloatStrip.engagedStrip → engagesPreMeasure, including the
//  §7.1 C==0 balance case via provableStripInkPx), and this layout is
//  TOTAL: the only reachable in-layout failures are contract breaches
//  (spec/subview desync, a crushed 0-height window), answered by the same
//  honest degraded render Compose documents — a flush stack of the
//  measured children, logged, never silent.
//

// SwiftUI for the Layout protocol; the geometry core stays view-free in
// MulticolFloatStripPlan.swift (the FS pin table's home).
import SwiftUI

extension MulticolFloatStrip {

    /// The one geometry answer both Layout passes share — resolves the
    /// live block-size proposal into the plan's H and delegates to the
    /// FS-table [plan]:
    ///  · a DEFINITE positive proposal is H itself — the same live-
    ///    constraints basis the Compose measure half gates on (its
    ///    min==max height constraints; here the container's declared
    ///    frame proposes its content-box height down to every slice);
    ///  · nil / infinite (SwiftUI's ideal and max probes, or an
    ///    auto-height chain) has NO fragmentainer, so the spec-true
    ///    auto-height answer applies: columns exactly as tall as the
    ///    content — h = C under `column-fill: auto` (§7.2) and
    ///    h = ceil(C/N) under balance (§7.1) — realized by re-planning
    ///    with H := C, which the fill branch passes through and the
    ///    balance branch reduces (ceil(C/N) ≤ C for N ≥ 1);
    ///  · a zero/negative proposal (SwiftUI's min probe) has no geometry
    ///    (the plan's own H > 0 gate) — nil, and the caller answers the
    ///    probe with a zero-height claim.
    static func sliceGeometry(specs: [MulticolSpannerFlow.ChildSpec],
                              measuredHeightsPx: [Double],
                              proposedBlockSizePx: Double?,
                              columnFillAuto: Bool,
                              columnWidthPx: Double,
                              columnGapPx: Double,
                              columnCount: Int) -> StripPlan? {
        // The definite branch: the live proposal IS the column basis H.
        if let h = proposedBlockSizePx, h.isFinite, h > 0 {
            return plan(specs: specs, measuredHeightsPx: measuredHeightsPx,
                        columnBlockSize: h, columnFillAuto: columnFillAuto,
                        columnWidth: columnWidthPx, gapPx: columnGapPx,
                        columnCount: columnCount)
        }
        // Explicit zero/negative (the min probe): no fragmentainer at all.
        if let h = proposedBlockSizePx, h.isFinite { return nil }
        // Probe branch, step 1: C (the strip ink extent) does not depend
        // on H, so ANY positive H recovers it through the one geometry
        // owner — no parallel strip walk here (one-owner rule).
        guard let probe = plan(specs: specs, measuredHeightsPx: measuredHeightsPx,
                               columnBlockSize: 1, columnFillAuto: columnFillAuto,
                               columnWidth: columnWidthPx, gapPx: columnGapPx,
                               columnCount: columnCount),
              probe.stripInkPx > 0 else { return nil }
        // Step 2: re-plan with H := C — fill keeps C, balance reduces to
        // ceil(C/N) via its min(H, ceil(C/N)) term (see the doc above).
        return plan(specs: specs, measuredHeightsPx: measuredHeightsPx,
                    columnBlockSize: probe.stripInkPx,
                    columnFillAuto: columnFillAuto,
                    columnWidth: columnWidthPx, gapPx: columnGapPx,
                    columnCount: columnCount)
    }
}

/// ONE column window of the float strip: hosts a full clone of the strip
/// content and shows its css-break-3 §4 slice.  Composed once per used
/// column by the renderer's W3 row (band 0..<N), always under the seam's
/// zero-flow environment — engagement was decided there, by the shared
/// pre-measure predicate (see the file banner for the discipline mapping).
@available(iOS 16.0, *)
struct MulticolFloatStripSliceLayout: Layout {
    /// The engaged container's proven per-child specs, in the exact
    /// contentOrPlaceholder order (engagement bars leading text, so the
    /// subviews are the sorted in-flow children 1:1).
    let specs: [MulticolSpannerFlow.ChildSpec]
    /// §7.2 sequential fill (auto) vs the initial §7.1 balance.
    let columnFillAuto: Bool
    /// Which slice this window shows: content band [band·h, (band+1)·h).
    let bandIndex: Int
    /// N — the §3.4 used column count (the renderer resolved it through
    /// the same MulticolMath lane the greedy layout uses).
    let columnCount: Int
    /// W — the §3.4 used per-column width; every slice claims exactly W
    /// so the row's HStack reproduces the i·(W+G) column origins.
    let columnWidthPx: Double
    /// G — the used column-gap (feeds the plan's fragment geometry only;
    /// the row's HStack realizes the inline gap structurally).
    let columnGapPx: Double

    /// The shared per-pass geometry: measure every clone child ONCE at
    /// column width with unbounded block-size — floats report zero flow
    /// height under the seam's published plan, so each measured height IS
    /// the child's in-flow strip extent (the Compose measure half's exact
    /// contract) — then the FS-table plan.
    private func geometry(heightPx: CGFloat?, subviews: Subviews) -> MulticolFloatStrip.StripPlan? {
        // Specs must align 1:1 with the clone's subviews — a child that
        // composed differently than the renderer predicted desyncs every
        // strip offset (the same loud bail as the Compose measure half).
        guard specs.count == subviews.count else {
            PropertyTracker.logOnce(
                key: "multicol-float-strip-slice-mismatch",
                message: "multicol float strip: specs/subviews mismatch "
                    + "(\(specs.count) vs \(subviews.count)) — slice content flush-stacked")
            return nil
        }
        // The zero-flow measure (unbounded block-size, column width).
        let heights = subviews.map {
            Double($0.sizeThatFits(ProposedViewSize(width: CGFloat(columnWidthPx), height: nil)).height)
        }
        // §9.5.2 clearance offsets + ledgers + the fill/balance used
        // column block-size, through the one shared resolver above.
        return MulticolFloatStrip.sliceGeometry(
            specs: specs, measuredHeightsPx: heights,
            proposedBlockSizePx: heightPx.map(Double.init),
            columnFillAuto: columnFillAuto,
            columnWidthPx: columnWidthPx, columnGapPx: columnGapPx,
            columnCount: columnCount)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        // Every slice claims exactly (W, h): W is the used column width
        // (the composed geometry — a live width disagreement would desync
        // the whole row, and no fixture in the proven family declares
        // one), h the plan's used column block-size (H under fill, the
        // balanced reduction under §7.1 — the refs' 85 in a 100px box).
        guard let sp = geometry(heightPx: proposal.height, subviews: subviews) else {
            // No geometry (min probe, or a contract breach already
            // logged): claim the window empty rather than invent a size.
            return CGSize(width: columnWidthPx, height: 0)
        }
        return CGSize(width: columnWidthPx, height: sp.columnBlockSizePx)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        // Re-derive from the GRANTED height: the size pass answered h, so
        // re-planning with H := h is a fixed point on both branches (fill
        // passes it through; balance's min(h, ceil(C/N)) = h because C is
        // proposal-independent) — the greedy layout's re-derive pattern.
        guard let sp = geometry(heightPx: bounds.height, subviews: subviews) else {
            // Honest degraded render for the unreachable declines — the
            // Compose measure half's exact fallback: flush-stack the
            // measured children (CSS 2.1 §9.4.1 minus the float ledgers
            // this branch has no plan for), logged by geometry()/here.
            PropertyTracker.logOnce(
                key: "multicol-float-strip-slice-degraded",
                message: "multicol float strip: slice geometry declined on a "
                    + "committed strip — clone flush-stacked (composition "
                    + "gates should make this unreachable)")
            var y = bounds.minY
            for sub in subviews {
                // Measure exactly as geometry() would have (zero-flow).
                let h = sub.sizeThatFits(ProposedViewSize(width: CGFloat(columnWidthPx), height: nil)).height
                sub.place(at: CGPoint(x: bounds.minX, y: y), anchor: .topLeading,
                          proposal: ProposedViewSize(width: CGFloat(columnWidthPx), height: nil))
                y += h
            }
            return
        }
        // Place every clone child at its strip offset SHIFTED UP by this
        // window's band start (−band·h) — FragmentGeometry's exact
        // translate (css-break-3 §4); the outer .clipped() keeps only the
        // [band·h, (band+1)·h) slice, so ink past the last band clips
        // exactly like the wave-10 N-cap (css-overflow-3 §2), and a band
        // past C shows nothing (an empty trailing column).
        let bandTop = Double(bandIndex) * sp.columnBlockSizePx
        for (index, sub) in subviews.enumerated() {
            sub.place(
                at: CGPoint(x: bounds.minX,
                            y: bounds.minY + CGFloat(sp.yOffsetsPx[index] - bandTop)),
                anchor: .topLeading,
                // The same proposal as the measure so the child lays out
                // at the size the plan measured.
                proposal: ProposedViewSize(width: CGFloat(columnWidthPx), height: nil))
        }
    }
}
