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
            out.append(Item(size: s, subviewIndices: [i], memberX: nil, memberY: nil))
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
                let plan = FloatRowPacking.layout(
                    widths: sizes.map { Double($0.width) },
                    heights: sizes.map { Double($0.height) },
                    availableWidth: .infinity,
                    // P6 — the br line-box strut for `<br clear>`-
                    // terminated runs; bare height otherwise.
                    strutPx: seg.strutted ? FloatRowPacking.strutPx : 0.0
                )
                out.append(Item(size: CGSize(width: plan.width, height: plan.height),
                                subviewIndices: idxs,
                                memberX: plan.x, memberY: plan.y))
            } else if let only = idxs.first {
                // Single segment — one child, stacked exactly like the
                // VStack proposed it (width-bounded, height-free).
                let s = subviews[only].sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
                out.append(Item(size: s, subviewIndices: [only], memberX: nil, memberY: nil))
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
                for (k, sub) in item.subviewIndices.enumerated() {
                    subviews[sub].place(
                        at: CGPoint(x: bounds.minX + CGFloat(mx[k]), y: y + CGFloat(my[k])),
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
