//
//  ColumnsApplier.swift
//  StyleEngine/columns — Phase 10 identity; wave 10 fragmentation.
//
//  Wave 10 (the fragmentation contract): the applier now owns the
//  DECISION of whether a multicol container's child fragments across
//  its columns (css-break-3 §4) and, when it does, the full geometry
//  plan the renderer's clip+translate pass draws. The plan is built
//  from pure inputs (ColumnsConfig + statically-resolved container
//  geometry + the child's property list) so ColumnsFragmentPlanTests
//  pins every gate without a render surface; the SwiftUI half
//  (ComponentRenderer.multicolFragmentRow) only consumes the result.
//

// CoreGraphics for CGFloat geometry; Foundation for the tracker.
import CoreGraphics
import Foundation

enum ColumnsApplier {
    // Phase 10 identity hook — ColumnsExtractor registers the family;
    // the config is consumed by the renderer (full-width fold, the
    // wave-9 column-box fill basis, and the wave-10 fragment plan).
    static func contribute(_ cfg: ColumnsConfig?) { _ = cfg }

    /// Everything the renderer's fragment pass needs: the S-table
    /// fragment list plus the used column geometry (for the HStack
    /// slot sizing/spacing that realizes the inline translation).
    struct FragmentPlan: Equatable {
        /// The css-break-3 §4 fragments (FragmentGeometry S-table).
        let fragments: [FragmentGeometry.Fragment]
        /// W — the §3 used per-column inline size in px.
        let columnWidthPx: CGFloat
        /// H — the column block-size (container content-box height).
        let columnBlockSizePx: CGFloat
        /// G — the used column-gap in px (the HStack spacing).
        let gapPx: CGFloat
    }

    /// Decide whether ONE in-flow child of a multicol container
    /// fragments, and build the geometry when it does. Nil = keep the
    /// existing unfragmented path (identity — every current
    /// non-overflowing multicol fixture renders byte-identically).
    ///
    /// - Parameters:
    ///   - columns: the container's typed multicol config (nil / not a
    ///     multicol container → never fragments, css-multicol-1 §2).
    ///   - verticalWritingMode: the container's writing-mode flag —
    ///     vertical/sideways modes are blocked-platform (contract:
    ///     horizontal-tb only) → bail + logOnce, never fragment.
    ///   - siblingCount: in-flow child count. The wave-10 diagnosis
    ///     covers the SINGLE-child fixture family; multi-child column
    ///     balancing (css-multicol-1 §7) is not built — logOnce when a
    ///     multi-child container would have needed it.
    ///   - contentWidthPx: U — the container's statically-definite
    ///     content-box width (nil = indefinite → no column geometry).
    ///   - contentHeightPx: H — the container's statically-definite
    ///     content-box height, the column block-size (nil/<=0 = auto
    ///     height → the container grows instead of fragmenting).
    ///   - gapPx: G — the used column-gap (the caller resolves it via
    ///     the same GapApplier lane as the wave-9 fill basis).
    ///   - childProperties: the child's IR list — its explicit
    ///     block-size C resolves from here.
    ///   - ctx: the CONTAINER's spacing context for the resolve (the
    ///     child inherits font-size unless redeclared, so em bases are
    ///     honest for this family; the fixture family is px anyway).
    ///   - wptCaptureMode: wave-21 lane MULTICOL — true ONLY in composed
    ///     WPT capture. Unlocks the AUTO-height branch below (§7.1: an
    ///     unconstrained multicol container always balances, so H =
    ///     ceil(C/N)); false (the default, and the whole dark-stage
    ///     corpus) keeps the definite-height-only wave-10 behaviour
    ///     byte-identically.
    static func fragmentPlan(columns: ColumnsConfig?,
                             verticalWritingMode: Bool,
                             siblingCount: Int,
                             contentWidthPx: CGFloat?,
                             contentHeightPx: CGFloat?,
                             gapPx: CGFloat,
                             childProperties: [IRProperty],
                             ctx: SpacingContext,
                             wptCaptureMode: Bool = false) -> FragmentPlan? {
        // §2 gate: only a multicol container (non-auto column-count or
        // column-width) establishes columns to fragment into.
        guard columns?.isMulticolContainer == true else { return nil }
        // Wave-21: a column-span:all child never fragments — it SPANS the
        // columns instead of flowing into them (css-multicol-1 §6.2); its
        // sequencing is the spanner-flow plan's job, not this pass'.
        // (Not a fallthrough: identity is the correct render for it.)
        if childProperties.contains(where: {
            $0.type == "ColumnSpan"
                && ValueExtractors.extractKeyword($0.data)?.uppercased() == "ALL" }) {
            return nil
        }
        // Blocked-platform bail (contract: horizontal-tb only): a
        // vertical writing mode flips the inline/block axes and this
        // pass' geometry would be wrong on both — logOnce (repo
        // no-silent-fallthrough rule) and keep the unfragmented path.
        if verticalWritingMode {
            PropertyTracker.logOnce(
                key: "multicol-fragment-vertical-writing",
                message: "multicol fragmentation: vertical writing-mode "
                    + "is blocked-platform (horizontal-tb only) — child "
                    + "renders unfragmented")
            return nil
        }
        // Definite inline size or bail: without a definite U there is no
        // §3 used column width to fragment at.
        guard let u = contentWidthPx else { return nil }
        // §3 used-value fit — the same math (and the same inputs) as
        // the wave-9 column-box fill basis, so the fragment slots and
        // the child's fill width agree by construction.
        guard let used = MulticolMath.usedColumns(
                availableWidthPx: Double(u),
                requestedCount: columns?.count,
                requestedWidthPx: columns?.widthPx,
                gapPx: Double(gapPx)) else { return nil }
        // H — the column block-size to break at, and C — the child's
        // laid-out block-size. Only an explicit Height/BlockSize resolves
        // statically (SizeExtractor + SizeApplierResolve, the same lane
        // the flex main plan uses); auto/content-sized children bail to
        // identity (their laid-out size is not statically knowable).
        let childSize = SizeExtractor.extract(from: childProperties)
        let h: CGFloat
        let c: CGFloat
        if let hh = contentHeightPx, hh > 0 {
            // Wave-10 definite-height branch (css-break-3 §4.1): H is the
            // container's content-box height; the child's percent height
            // resolves against it (CSS 2.1 §10.5).
            h = hh
            guard let cc = SizeApplierResolve.exact(childSize.height, ctx: ctx,
                                                    parent: hh) else { return nil }
            c = cc
        } else if wptCaptureMode {
            // Wave-21 AUTO-height branch (B-RC5, capture-only): §7.1 — an
            // unconstrained multicol container ALWAYS balances, so the
            // used column block-size is H = ceil(C / N) (the SP-table's
            // sole-flow fragmentainer; as-column-flex-item: 160/4 → 40).
            // Percent heights have no basis under an auto parent and
            // degrade to auto (allowPercent: false) → identity bail.
            guard let cc = SizeApplierResolve.exact(childSize.height, ctx: ctx,
                                                    parent: 0,
                                                    allowPercent: false) else { return nil }
            c = cc
            h = (cc / CGFloat(used.count)).rounded(.up)
            // A degenerate balanced H (0-height child) has nothing to
            // fragment — identity.
            guard h > 0 else { return nil }
        } else {
            // Dark stage keeps the wave-10 contract: auto-height multicol
            // grows to fit instead of fragmenting (frozen behaviour).
            return nil
        }
        // The geometry itself: nil when C <= H (S2 identity — the fits
        // case never reaches a break point, css-break-3 §4).
        guard let frags = FragmentGeometry.fragments(
                childBlockSize: Double(c),
                columnBlockSize: Double(h),
                columnWidth: used.widthPx,
                gapPx: Double(gapPx),
                columnCount: used.count) else { return nil }
        // Single-child family only: multi-child containers need real
        // column balancing (css-multicol-1 §7) to know each child's
        // start offset — not built. logOnce (no silent fallthrough)
        // and keep the pre-wave-10 unfragmented flow.
        guard siblingCount == 1 else {
            PropertyTracker.logOnce(
                key: "multicol-fragment-multi-child",
                message: "multicol fragmentation: multi-child column "
                    + "balancing not implemented — children render "
                    + "unfragmented")
            return nil
        }
        // All gates passed — hand the renderer the full draw plan.
        return FragmentPlan(fragments: frags,
                            columnWidthPx: CGFloat(used.widthPx),
                            columnBlockSizePx: h,
                            gapPx: gapPx)
    }
}
