//
//  InlineAtomBlockLayout.swift
//  Renderer — wave-20 lane W3.
//
//  The block container's inline-atom-aware child arrangement: the
//  SwiftUI adapter over the pure InlineAtomFlow twins (StyleEngine/
//  layout/InlineAtomFlow.swift ↔ Compose layout/InlineAtomFlow.kt).
//  The ComponentRenderer `.block` case routes here INSTEAD of the plain
//  VStack when — composed-WPT capture only (pin P19) — the sorted
//  in-flow children contain a packable inline atom run (≥2 consecutive
//  UA-widget / text-only-anchor siblings, InlineAtomFlow.segment).
//  Runs pack into wrapped, baseline-ish rows per CSS 2.1 §9.4.2 (the
//  css-ui appearance-auto-001 widget soup); every other child stacks
//  vertically exactly like the VStack it replaces — the
//  FloatBlockLayout structure, atom-flavoured.
//

// SwiftUI for the Layout protocol; the packing core is view-free.
import SwiftUI

/// Block flow with inline atom rows (iOS 16 Layout — same availability
/// discipline as FloatBlockLayout / MulticolGreedyLayout).
@available(iOS 16.0, *)
struct InlineAtomBlockLayout: Layout {
    /// The pure segmentation over the SORTED child list (computed by the
    /// renderer from the same array the ForEach renders, so subview
    /// indices line up by construction).
    let segments: [InlineAtomFlow.Segment]
    /// Per-CHILD atom specs from the shared UAWidgetIntrinsics table,
    /// index-aligned with the sorted child list (NOT with subviews —
    /// the leading text shifts subview indices by `leadingCount`).
    let specs: [UAWidgetIntrinsics.AtomSpec]
    /// Leading non-child subviews (the mixed-content text label the
    /// renderer prepends) — stacked before segment 0, like the VStack.
    let leadingCount: Int
    /// The VStack's `spacing: gap.row` twin — applied between stacked
    /// top-level items (leading text, singles, runs), not inside rows.
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
        /// Run-relative member y (baseline-aligned row top, P17).
        let memberY: [Double]?
        /// Per-member size proposals for the place pass (fixed table
        /// axes must be RE-proposed so the replica keeps the box).
        let memberProposals: [ProposedViewSize]?
    }

    /// Measure every top-level item once from one proposal — the size
    /// and place passes derive the identical plan (pure inputs cannot
    /// drift; the FloatBlockLayout shared-plan pattern).
    private func items(proposal: ProposedViewSize, subviews: Subviews) -> [Item] {
        // Accumulator, stacked order: leading text first, then segments.
        var out: [Item] = []
        // Leading mixed-content text stacks like a VStack child: propose
        // the container's width so the label wraps where the VStack let it.
        for i in 0..<leadingCount where i < subviews.count {
            let s = subviews[i].sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
            out.append(Item(size: s, subviewIndices: [i],
                            memberX: nil, memberY: nil, memberProposals: nil))
        }
        // Segments over the children — subview index = leadingCount + child index.
        for seg in segments {
            // Guard against a subview/segment count drift (a renderer
            // bug, not a layout state): drop the out-of-range tail loudly
            // rather than crash the capture.
            let idxs = seg.indices.map { leadingCount + $0 }.filter { $0 < subviews.count }
            if seg.isRun {
                // P18 — the run members' atom specs (child-indexed; a
                // missing spec degrades to a measured, bottom-aligned,
                // zero-margin atom rather than crashing).
                let memberSpecs = seg.indices.map { ci in
                    ci < specs.count ? specs[ci]
                        : UAWidgetIntrinsics.AtomSpec(fixedWpx: nil, fixedHpx: nil, descentPx: 0.0)
                }
                // Fixed table axes become exact proposals; free axes
                // measure the replica's own content (nil axis). CSS px
                // == pt at the capture scale (FloatBlockLayout precedent).
                let proposals = memberSpecs.map { s in
                    ProposedViewSize(width: s.fixedWpx.map { CGFloat($0) },
                                     height: s.fixedHpx.map { CGFloat($0) })
                }
                // Measure each member under its proposal, then let the
                // TABLE override the fixed axes verbatim — SwiftUI views
                // may decline a proposal (Text hugs its glyphs), but the
                // atom BOX is the table's promise (W2 paints inside it).
                let sizes = zip(idxs, zip(proposals, memberSpecs)).map { sub, ps -> CGSize in
                    let measured = subviews[sub].sizeThatFits(ps.0)
                    return CGSize(width: ps.1.fixedWpx.map { CGFloat($0) } ?? measured.width,
                                  height: ps.1.fixedHpx.map { CGFloat($0) } ?? measured.height)
                }
                // P16/P17 — the pure flow plan at the container's CONTENT
                // width (the css-ui refs' 500px container wraps here; an
                // unbounded proposal simply never wraps).
                let plan = InlineAtomFlow.layout(
                    widths: sizes.map { Double($0.width) },
                    heights: sizes.map { Double($0.height) },
                    descents: memberSpecs.map { $0.descentPx },
                    marginStarts: memberSpecs.map { $0.marginStartPx },
                    marginEnds: memberSpecs.map { $0.marginEndPx },
                    availableWidth: proposal.width.map { Double($0) } ?? .infinity,
                    gapPx: UAWidgetIntrinsics.atomGapPx,
                    // P17b (fix 4) — the §10.8.1 line-box strut: rows of
                    // short atoms (checkbox row, the lone progress bar)
                    // still get the block's 16/4 text metrics (wave-22
                    // re-solve), matching
                    // the ref's row pitches.
                    strutAscentPx: UAWidgetIntrinsics.strutAscentPx,
                    strutDescentPx: UAWidgetIntrinsics.strutDescentPx
                )
                out.append(Item(size: CGSize(width: plan.width, height: plan.height),
                                subviewIndices: idxs,
                                memberX: plan.x, memberY: plan.y,
                                memberProposals: proposals))
            } else if let only = idxs.first {
                // Single segment — one child, stacked exactly like the
                // VStack proposed it (width-bounded, height-free).
                let s = subviews[only].sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
                out.append(Item(size: s, subviewIndices: [only],
                                memberX: nil, memberY: nil, memberProposals: nil))
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
        // (widest item wide, items + spacing tall). An over-wide row
        // reports its full extent and paints unclipped — the same
        // overflow contract the float layout pins.
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
                // Run: place each member at its packed origin, RE-
                // proposing the fixed table axes so the replica keeps
                // the promised atom box (P18) and paints unclipped past
                // it when its content overflows.
                for (k, sub) in item.subviewIndices.enumerated() {
                    subviews[sub].place(
                        at: CGPoint(x: bounds.minX + CGFloat(mx[k]), y: y + CGFloat(my[k])),
                        anchor: .topLeading,
                        proposal: item.memberProposals?[k] ?? .unspecified)
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
