//
//  FloatBlockLayout.swift
//  Renderer — wave-19 lane FLOAT.
//
//  The block container's float-aware child arrangement: the SwiftUI
//  adapter over the pure FloatRowPacking twins (StyleEngine/layout/
//  FloatRowPacking.swift ↔ Compose layout/FloatRowPacking.kt). The
//  ComponentRenderer `.block` case routes here INSTEAD of the plain
//  VStack when — composed-WPT capture only (pin P8) — the sorted
//  in-flow children contain a packable float run (≥2 consecutive
//  left-floating siblings, FloatRowPacking.segment). Runs lay out
//  side-by-side per CSS 2.1 §9.5 (the descendant-static-position-001
//  green+grey pair, justify-self-001's 3/2/5/4 rows); every other
//  child stacks vertically exactly like the VStack it replaces.
//

// SwiftUI for the Layout protocol; the packing core is view-free.
import SwiftUI

/// Block flow with float rows (iOS 16 Layout — same availability
/// discipline as CSSFlexLayout / MulticolGreedyLayout).
@available(iOS 16.0, *)
struct FloatBlockLayout: Layout {
    /// The pure segmentation over the SORTED child list (computed by the
    /// renderer from the same array the ForEach renders, so subview
    /// indices line up by construction).
    let segments: [FloatRowPacking.Segment]
    /// Leading non-child subviews (the mixed-content text label the
    /// renderer prepends) — stacked before segment 0, like the VStack.
    let leadingCount: Int
    /// The VStack's `spacing: gap.row` twin — applied between stacked
    /// top-level items (leading text, singles, runs), not inside runs.
    let spacing: CGFloat

    /// One measured top-level item: its stacked size plus — for runs —
    /// the member geometry the place pass re-applies.
    private struct Item {
        /// The item's contribution to the vertical stack (w × h).
        let size: CGSize
        /// Subview indices this item owns, in order.
        let subviewIndices: [Int]
        /// Run-relative member origins (nil for stacked singles).
        let memberX: [Double]?
        /// Run-relative member y (the member's float-row top).
        let memberY: [Double]?
        /// Wave-20 W3 (P13): true for RIGHT runs — the place pass then
        /// anchors the run extent at the container's RIGHT edge
        /// (§9.5.1 rule 1 mirrored) instead of the leading edge.
        let rightAnchored: Bool
        /// Wave-20 fix 6: the members' measured margin-box widths (same
        /// order as memberX). The place pass needs them to COUNTER-mirror
        /// under an RTL ambient layoutDirection — SwiftUI's mirror is
        /// x' = W − x − w, so undoing it requires each member's width.
        let memberW: [Double]?
    }

    /// Wave-20 fix 6 — the RTL counter-mirror, pure and pinned
    /// (FloatRowPackingTests.testDescendantStaticPosition002RightRun…).
    ///
    /// The packing plans (FloatRowPacking.layout/layoutEnd) produce
    /// PHYSICAL x origins — the same numbers Compose places verbatim via
    /// Placeable.place(). SwiftUI's Layout engine, however, mirrors every
    /// place(at:) x about the layout bounds (x' = bounds.width − x − w)
    /// whenever the ambient layoutDirection is RTL — probe-verified in
    /// SkepticRtlGridPlacementTests (an ImageRenderer pixel probe showed
    /// pre-mirrored physical origins double-flip back to LTR geometry).
    /// descendant-static-position-002's container declares Direction:RTL
    /// (TypographyApplier maps it onto \.layoutDirection), so the right
    /// run's layoutEnd origins were mirrored a SECOND time and the run
    /// painted in REVERSED order — the first right float (green) landed
    /// LEFTMOST where CSS 2.1 §9.5.1 rule 1 (mirrored) gives the first
    /// right float the RIGHTMOST slot (the ref's gray x40-59 / green
    /// x60-79 pattern; Android, mirror-free place(), passes 002).
    /// Fix: under RTL, pre-apply the SAME involution to the intended
    /// physical origin so the engine's flip restores it exactly.
    static func placedX(intendedX: CGFloat, memberWidth: CGFloat,
                        boundsWidth: CGFloat, rtl: Bool) -> CGFloat {
        // LTR: the engine places verbatim — hand it the physical origin.
        guard rtl else { return intendedX }
        // RTL: the engine will compute W − x − w; feeding it W − x − w
        // yields W − (W − x − w) − w = x, the intended physical origin.
        return boundsWidth - intendedX - memberWidth
    }

    /// Measure every top-level item once from one proposal — the size
    /// and place passes derive the identical plan (pure inputs cannot
    /// drift; the MulticolGreedyLayout shared-plan pattern).
    private func items(proposal: ProposedViewSize, subviews: Subviews) -> [Item] {
        // Accumulator, stacked order: leading text first, then segments.
        var out: [Item] = []
        // Leading mixed-content text stacks like a VStack child: propose
        // the container's width so the label wraps where the VStack let it.
        for i in 0..<leadingCount where i < subviews.count {
            let s = subviews[i].sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
            out.append(Item(size: s, subviewIndices: [i], memberX: nil, memberY: nil,
                            rightAnchored: false, memberW: nil))
        }
        // Segments over the children — subview index = leadingCount + child index.
        for seg in segments {
            // Guard against a subview/segment count drift (a renderer
            // bug, not a layout state): drop the out-of-range tail loudly
            // rather than crash the capture.
            let idxs = seg.indices.map { leadingCount + $0 }.filter { $0 < subviews.count }
            if seg.isRun {
                // P7 — unbounded measure (.unspecified): each float's own
                // size chain decides its margin-box (margins are outer
                // padding in this engine), the absposOverflowMeasure
                // discipline for shrink-to-fit abspos ancestors.
                let sizes = idxs.map { subviews[$0].sizeThatFits(.unspecified) }
                // P3/P4 — pure greedy packing at UNBOUNDED width (see
                // FloatRowPacking.layout's doc: the synthetic IR frame
                // width must not drive the wrap for the WPT corpus).
                // Right runs take the mirrored plan (P12) — shared rows,
                // x reflected against the run extent, anchored at place.
                let widths = sizes.map { Double($0.width) }
                let heights = sizes.map { Double($0.height) }
                // P6 — the br line-box strut for `<br clear>`-terminated
                // runs; bare height otherwise.
                let strut = seg.strutted ? FloatRowPacking.strutPx : 0.0
                let plan = seg.rightRun
                    ? FloatRowPacking.layoutEnd(widths: widths, heights: heights,
                                                availableWidth: .infinity, strutPx: strut)
                    : FloatRowPacking.layout(widths: widths, heights: heights,
                                             availableWidth: .infinity, strutPx: strut)
                out.append(Item(size: CGSize(width: plan.width, height: plan.height),
                                subviewIndices: idxs,
                                memberX: plan.x, memberY: plan.y,
                                rightAnchored: seg.rightRun,
                                // fix 6: widths ride along for the RTL
                                // counter-mirror in the place pass.
                                memberW: widths))
            } else if let only = idxs.first {
                // Single segment — one child, stacked exactly like the
                // VStack proposed it (width-bounded, height-free).
                let s = subviews[only].sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
                out.append(Item(size: s, subviewIndices: [only], memberX: nil, memberY: nil,
                                rightAnchored: false, memberW: nil))
            }
        }
        return out
    }

    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        // Empty container → zero, like an empty VStack.
        guard !subviews.isEmpty else { return .zero }
        // Measure once; report the natural stacked size the VStack would
        // (widest item wide, items + spacing tall). An over-wide run
        // reports its full extent — the outer .frame(alignment:
        // .topLeading) anchors it and lets the ink overflow right, the
        // same overflow contract the browser-ref paints.
        let its = items(proposal: proposal, subviews: subviews)
        let width = its.map(\.size.width).max() ?? 0
        // Σ heights + spacing BETWEEN items (n-1 gaps, VStack semantics).
        let height = its.map(\.size.height).reduce(0, +)
            + spacing * CGFloat(max(0, its.count - 1))
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout ()) {
        // Nothing to place for an empty container.
        guard !subviews.isEmpty else { return }
        // Re-derive the identical plan for this proposal (pure inputs).
        let its = items(proposal: proposal, subviews: subviews)
        // Vertical cursor from the top-leading corner — block flow.
        var y = bounds.minY
        for item in its {
            if let mx = item.memberX, let my = item.memberY {
                // Run: place each member at its packed origin, proposing
                // the SAME unbounded size the measure pass used so the
                // member keeps its specified geometry (P7) and paints
                // unclipped past the reported box (overflow:visible).
                // P13 — RIGHT runs anchor their extent at the container's
                // right edge (§9.5.1 rule 1 mirrored): the run-relative
                // origins shift by (bounds.width − extent), which is 0 in
                // the shrink-to-fit case (container == widest item) and
                // negative when overconstrained — right floats then
                // overflow LEFT, exactly like the browser.
                let runX = item.rightAnchored
                    ? bounds.maxX - item.size.width
                    : bounds.minX
                // fix 6: the plans' origins are PHYSICAL; under an RTL
                // ambient layoutDirection SwiftUI mirrors every place()
                // x about the bounds, so pre-mirror (placedX) to make
                // the engine's flip a no-op — Compose parity restored
                // (descendant-static-position-002's Direction:RTL
                // right-float pair kept its first-float-rightmost order).
                let rtl = subviews.layoutDirection == .rightToLeft
                for (k, sub) in item.subviewIndices.enumerated() {
                    // Intended physical origin, bounds-relative — the
                    // exact coordinate Compose's adapter places.
                    let physicalX = (runX - bounds.minX) + CGFloat(mx[k])
                    // Member width for the involution (memberW rides the
                    // Item precisely for this; runs always carry it).
                    let w = CGFloat(item.memberW?[k] ?? 0)
                    subviews[sub].place(
                        at: CGPoint(
                            x: bounds.minX + Self.placedX(intendedX: physicalX,
                                                          memberWidth: w,
                                                          boundsWidth: bounds.width,
                                                          rtl: rtl),
                            y: y + CGFloat(my[k])),
                        anchor: .topLeading,
                        proposal: .unspecified)
                }
            } else if let only = item.subviewIndices.first {
                // Stacked single: leading-aligned, width-bounded — the
                // exact VStack(alignment: .leading) placement.
                subviews[only].place(
                    at: CGPoint(x: bounds.minX, y: y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(width: proposal.width, height: nil))
            }
            // Advance past this item + the inter-item spacing.
            y += item.size.height + spacing
        }
    }
}
