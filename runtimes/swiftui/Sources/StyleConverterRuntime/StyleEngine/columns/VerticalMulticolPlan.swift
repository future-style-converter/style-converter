//
//  VerticalMulticolPlan.swift
//  StyleEngine/columns — wave 47 (lane Z2).
//
//  The VERTICAL-writing-mode fragment plan builder — the decision half
//  ColumnsApplier.fragmentPlan routes into when the container's writing
//  mode is vertical (capture-gated at the route). Split out of
//  ColumnsApplier.swift (the MulticolClonePlan precedent — repo file-size
//  rule); the Compose measure-pass twin is VerticalMulticolMeasure.kt, and
//  both consume the shared V-table (VerticalFragmentGeometry).
//
//  Axes recap: the container's INLINE extent (content HEIGHT) fits the
//  column boxes (colH each, stacked vertically); its BLOCK extent (content
//  WIDTH) is the fragmentainer W the sole child's block extent C — the
//  child's used WIDTH — breaks at.
//

// CoreGraphics for CGFloat geometry; Foundation for the tracker.
import CoreGraphics
import Foundation

extension ColumnsApplier {

    /// Build the vertical plan, or nil for every shape outside the
    /// sole-child contract (the caller then keeps its honest logged bail).
    /// Gates, each named (no-silent-fallthrough — the decline reasons that
    /// differ from the generic bail log their own record):
    ///  - exactly ONE in-flow child (multi-child vertical balancing is not
    ///    built — the generic bail covers it);
    ///  - NOT a post-load-extracted wire: a child carrying baked physical
    ///    Width AND Height already encodes the browser's used layout —
    ///    including its fragmentation — so re-fragmenting would double-apply
    ///    (the anchor-position-multicol family PASSES today on that frozen
    ///    render; Compose twin: ChildSpec.bakedPhysicalSize);
    ///  - definite content WIDTH (W) and HEIGHT (U, the inline extent);
    ///  - used count ≥ 2 (§8.2: one column overflows, never clips);
    ///  - a statically-resolvable child block extent C — the child's
    ///    `block-size` (or a sole physical `width`), px-resolvable; percent
    ///    has no honest basis here and declines.
    static func verticalFragmentPlan(columns: ColumnsConfig?,
                                     siblingCount: Int,
                                     contentWidthPx: CGFloat?,
                                     contentHeightPx: CGFloat?,
                                     gapPx: CGFloat,
                                     childProperties: [IRProperty],
                                     ctx: SpacingContext,
                                     blockRtl: Bool) -> FragmentPlan? {
        // Sole-child contract (the caller's generic bail logs the rest).
        guard siblingCount == 1 else { return nil }
        // The post-load bake gate (doc above) — its own log, because this
        // decline is a PROTECTION, not a missing feature.
        let hasBakedWidth = childProperties.contains { $0.type == "Width" }
        let hasBakedHeight = childProperties.contains { $0.type == "Height" }
        if hasBakedWidth && hasBakedHeight {
            PropertyTracker.logOnce(
                key: "multicol-vertical-baked-layout",
                message: "vertical multicol: post-load-extracted baked "
                    + "layout — fragmentation already encoded, frozen path kept")
            return nil
        }
        // Both container extents must be statically definite: W is the
        // fragmentainer block size, U fits the column boxes.
        guard let w = contentWidthPx, w > 0,
              let u = contentHeightPx, u > 0 else { return nil }
        // §3.4 used count against the INLINE extent (vertical basis). The
        // shared MulticolMath fit also yields the per-column INLINE size —
        // colH here — so the plan and any fill basis agree by construction.
        guard let used = MulticolMath.usedColumns(
                availableWidthPx: Double(u),
                requestedCount: columns?.count,
                requestedWidthPx: columns?.widthPx,
                gapPx: Double(gapPx)) else { return nil }
        // One column overflows instead of clipping (§8.2) — the frozen
        // path's render is the honest one there.
        guard used.count >= 2 else { return nil }
        let colH = Double(used.widthPx)
        guard colH > 0 else { return nil }
        // C — the child's block extent: its logical `block-size` (the raw
        // child list maps it to the height slot — the child's own list
        // carries no inherited WritingMode), or a sole physical `width`
        // (physical width IS the block size under vertical modes). Percent
        // declines (`allowPercent: false`): its basis is the fragmented
        // flow itself.
        let childSize = SizeExtractor.extract(from: childProperties)
        let blockSlot = hasBakedWidth ? childSize.width : childSize.height
        guard let c = SizeApplierResolve.exact(blockSlot, ctx: ctx,
                                               parent: 0,
                                               allowPercent: false) else { return nil }
        // The shared V-table (fits case included — vertical-rl right-aligns
        // even fitting content; see VerticalFragmentGeometry's doc).
        guard let frags = VerticalFragmentGeometry.fragments(
                childBlockSize: Double(c),
                containerBlockSize: Double(w),
                columnInlineSize: colH,
                gapPx: Double(gapPx),
                columnCount: used.count,
                blockRtl: blockRtl) else { return nil }
        // Slot geometry for the renderer's VStack: width W × height colH
        // per column, gap G between them (FragmentPlan.vertical's doc).
        return FragmentPlan(fragments: frags,
                            columnWidthPx: w,
                            columnBlockSizePx: CGFloat(colH),
                            gapPx: gapPx,
                            vertical: true,
                            verticalBlockRtl: blockRtl)
    }
}
