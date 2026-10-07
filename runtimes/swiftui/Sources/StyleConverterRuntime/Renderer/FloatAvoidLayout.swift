//
//  FloatAvoidLayout.swift
//  Renderer — wave-53 lane L4 (float-avoid).
//
//  The SwiftUI adapter over the pure FloatAvoidPlan (StyleEngine/layout/
//  FloatAvoidPlan.swift ↔ Compose layout/FloatAvoidPlan.kt + its adapter
//  FloatAvoidLayout.kt). The ComponentRenderer `.block` branch routes here
//  INSTEAD of the plain VStack when — composed-WPT capture only — the sorted
//  in-flow children are a proven float-then-BFC list (FloatAvoidPlan.shape):
//  floats by CSS 2.1 §9.5.1, the BFC root beside them per §9.5, the box
//  reporting the BFC bottom (§10.6.3). Raster-pinned by
//  FloatAvoidLayoutRasterTests (ImageRenderer under Catalyst).
//

// SwiftUI for the Layout protocol; the placement math is view-free.
import SwiftUI

/// Float-avoiding block flow (iOS 16 Layout — FloatBlockLayout's availability).
@available(iOS 16.0, *)
struct FloatAvoidLayout: Layout {
    /// The gated shape over the SAME sorted children contentOrPlaceholder
    /// renders (no leading text: the gate refuses own text), so subview i is
    /// child i by construction — k floats, then the BFC.
    let shape: FloatAvoidPlan.Shape
    /// The container id, for the one node-count breadcrumb below.
    let componentId: String

    /// One solve from one proposal: the size and place passes derive the
    /// identical plan (the FloatBlockLayout shared-plan pattern). Nil when the
    /// subview count drifted from the shape — a renderer bug, logged once.
    private func solve(_ proposal: ProposedViewSize, _ subviews: Subviews) -> FloatAvoidPlan.Placement? {
        let k = shape.sides.count
        guard subviews.count == k + 1 else {
            // No silent fallthrough: the caller stacks like the VStack it replaced.
            _ = PropertyTracker.logOnce(key: "float-avoid:node-count:\(componentId)",
                                        message: "FloatAvoidLayout: \(subviews.count) subviews != \(k + 1) — "
                                            + "stacked fallback for component \(componentId)")
            return nil
        }
        // Floats measure UNSPECIFIED (FloatBlockLayout's P7): their own px
        // frames decide the margin box, and a right float's
        // `.frame(maxWidth: .infinity, alignment: .topTrailing)` collapses to
        // its ideal width instead of claiming the container.
        let floats = (0..<k).map { subviews[$0].sizeThatFits(.unspecified) }
        // The BFC measures like a VStack child (width-bounded, height-free), so
        // its own content and height are what the frozen path drew.
        let bfc = subviews[k].sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
        // Incoming width: the proposal (the container's content width); with
        // none, the widest child — a shrink-to-fit reading.
        let incoming = Double(proposal.width ?? (floats + [bfc]).map(\.width).max() ?? 0)
        // Points == CSS px at the capture scale, so the plan's px apply as-is.
        return FloatAvoidPlan.place(shape,
                                    floatWidths: floats.map { Double($0.width) },
                                    floatHeights: floats.map { Double($0.height) },
                                    incomingWidth: incoming,
                                    bfcHeight: Double(bfc.height))
    }

    /// VStack(alignment: .leading, spacing: 0) geometry for the drift fallback.
    private func stacked(_ proposal: ProposedViewSize, _ subviews: Subviews) -> [CGSize] {
        subviews.map { $0.sizeThatFits(ProposedViewSize(width: proposal.width, height: nil)) }
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        // Drift fallback: the natural stacked size, like the VStack.
        guard let plan = solve(proposal, subviews) else {
            let s = stacked(proposal, subviews)
            return CGSize(width: s.map(\.width).max() ?? 0, height: s.map(\.height).reduce(0, +))
        }
        // A block box fills its containing block's width; its auto height is
        // the BFC bottom (floats excluded — CSS 2.1 §10.6.3).
        return CGSize(width: proposal.width ?? CGFloat(plan.width), height: CGFloat(plan.height))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        // Drift fallback: top-to-bottom, leading-aligned — the VStack's placement.
        guard let plan = solve(proposal, subviews) else {
            var y = bounds.minY
            for (sub, size) in zip(subviews, stacked(proposal, subviews)) {
                sub.place(at: CGPoint(x: bounds.minX, y: y), anchor: .topLeading,
                          proposal: ProposedViewSize(width: proposal.width, height: nil))
                y += size.height
            }
            return
        }
        let k = shape.sides.count
        // The plan's origins are PHYSICAL; under an RTL ambient direction
        // SwiftUI mirrors place() x about the bounds, so pre-mirror with
        // FloatBlockLayout's involution (G3 refuses an RTL container, so this
        // is belt-and-braces for an RTL ancestor outside the wire).
        let rtl = subviews.layoutDirection == .rightToLeft
        for i in 0..<subviews.count {
            // Floats keep the unspecified proposal they were measured with
            // (their specified geometry, unclipped); the BFC its VStack one.
            let p: ProposedViewSize = i < k ? .unspecified : ProposedViewSize(width: proposal.width, height: nil)
            let w = subviews[i].sizeThatFits(p).width
            // Wire order: floats, then the BFC, so its inline content paints
            // over the floats (CSS 2.1 Appendix E step 7 above step 5).
            subviews[i].place(
                at: CGPoint(x: bounds.minX + FloatBlockLayout.placedX(intendedX: CGFloat(plan.x[i]), memberWidth: w,
                                                                       boundsWidth: bounds.width, rtl: rtl),
                            y: bounds.minY + CGFloat(plan.y[i])),
                anchor: .topLeading, proposal: p)
        }
    }
}
