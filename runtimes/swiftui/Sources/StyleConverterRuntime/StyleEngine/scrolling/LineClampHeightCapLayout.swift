//
//  LineClampHeightCapLayout.swift
//  StyleEngine/scrolling — Wave 46 (lane Y1): the layout half of the
//  block-level line-clamp cap (the pure half is LineClampCap.swift).
//
//  Twin of Compose's `Modifier.lineClampHeightCap(capPx)` (scrolling/
//  LineClampCap.kt): cap this node's LAYOUT height at the N-line-box
//  budget while laying the content out on an UNBOUNDED block axis, so the
//  visible prefix is the same first N lines the unclamped layout produces
//  (css-overflow-4 §5.3.2: discarding content must not re-flow what
//  precedes it). The discarded tail hangs below the reported box; the
//  Y-only clip chained outside makes its ink unpaintable (§4.3 "is not
//  rendered"), and X is NEVER clipped: a clamp creates no inline-axis
//  clip (block-ellipsis-013's preserved first line paints past the border
//  box's right edge in the ref, and must keep doing so).
//
//  Why a `Layout` and not `.frame(maxHeight:)`: a frame with a max height
//  PROPOSES that height to its child, and a SwiftUI VStack handed less
//  height than its content redistributes / compresses its Texts — the
//  first N lines would no longer be the natural first N lines. The Layout
//  below proposes `height: nil` (the child's ideal height), reports the
//  capped size, and places the child at its FULL natural size anchored
//  top-leading — the same measure-unbounded-then-cap contract as the
//  Compose modifier.
//

import SwiftUI

/// Single-subview layout: natural size on the block axis, capped report.
struct LineClampHeightCapLayout: Layout {
    /// The N-line-box budget in pt (LineClampCap.resolveCapPx).
    let capPx: CGFloat

    /// The child's natural size at the proposed width: the block axis is
    /// deliberately unbounded (`height: nil`) so wrapping and line layout
    /// are exactly what the unclamped container would produce.
    private func natural(_ sub: LayoutSubview, _ proposal: ProposedViewSize) -> CGSize {
        sub.sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
    }

    /// Report the child's width untouched and its height capped by the
    /// pure decision (LineClampCap.cappedHeight: kept within slack, else
    /// the ceil'd cap) — an explicit `height:` applied OUTSIDE this node
    /// (the sizing frame in applyBoxDecoration) still wins, exactly like
    /// CSS used-height wins over max-lines.
    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews, cache: inout ()) -> CGSize {
        guard let sub = subviews.first else { return .zero }
        let sz = natural(sub, proposal)
        return CGSize(width: sz.width,
                      height: LineClampCap.cappedHeight(measuredPx: sz.height, capPx: capPx))
    }

    /// Place the child at its FULL natural size (re-proposing the concrete
    /// measured size reproduces the measured line layout at render time),
    /// top-leading so its first N lines fill the capped box and the
    /// discarded tail hangs below it for the outer clip to erase.
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        guard let sub = subviews.first else { return }
        let sz = natural(sub, proposal)
        sub.place(at: bounds.origin, anchor: .topLeading, proposal: ProposedViewSize(sz))
    }
}

extension View {
    /// The renderer seam for the block-level line-clamp cap: identity
    /// (no extra view node) when `capPx` is nil — every clamp-less
    /// component keeps a byte-identical view tree — else the capped
    /// layout wrapped in a Y-only ink clip. Chain slot: INNERMOST of the
    /// box chain, on the flow container before applyBoxDecoration, so
    /// the budget is the content box's N line boxes and padding / border
    /// / sizing / paint all wrap the capped box.
    @ViewBuilder
    func engineLineClampCap(_ capPx: CGFloat?) -> some View {
        if let cap = capPx {
            LineClampHeightCapLayout(capPx: cap) { self }
                // css-overflow-4 §5.3.2 — the discarded lines' ink must not
                // paint; the block axis is bound to the capped box, the
                // inline axis stays open (AxisClipRect, the wave-18 clip
                // geometry shared with the overflow-x/-y appliers).
                .clipShape(AxisClipRect(clipX: false, clipY: true))
        } else {
            self
        }
    }
}
