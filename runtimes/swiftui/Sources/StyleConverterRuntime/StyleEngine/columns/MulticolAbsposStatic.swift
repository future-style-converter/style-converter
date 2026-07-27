//
//  MulticolAbsposStatic.swift
//  StyleEngine/columns — wave-21 lane MULTICOL (B-RC4b).
//
//  The static position of an abspos child of a MULTICOL container:
//  css-position-3 §3.1 anchors an inset-less absolutely-positioned box at
//  the position it would have had in flow — and inside a multicol
//  container that flow position lives in the SPANNER-AWARE column flow
//  (css-multicol-1 §6): an abspos following a `column-span: all` sibling
//  anchors in the post-spanner flow of COLUMN 1, at the spanner's bottom
//  edge (the wave-21 diagnosis; on iOS the abspos rides the positioned
//  overlay, whose anchor is the container's box origin — this module
//  computes the missing (x, y) shift from that origin).
//
//  Statically-resolved twin of the Compose path: Android's abspos IS a
//  measurable of the multicol Layout, so its STATIC slot comes from the
//  measured MulticolSpannerFlow plan; iOS overlays never measure against
//  the columns, so the SAME pure plan is fed with statically-resolved
//  sibling heights instead. The fixture family declares every sibling
//  height in px, so the answers are bit-identical (SP-table rows SP2/SP6).
//

// CoreGraphics for the CGSize offset the overlay consumes.
import CoreGraphics
// Foundation for the resolvers' numeric plumbing.
import Foundation

// Namespacing enum — all members static + pure (MulticolMath pattern).
enum MulticolAbsposStatic {

    /// The (x, y) shift from the positioned-overlay anchor (the
    /// container's box origin) to the abspos child's multicol static
    /// position. `.zero` — the identity, keeping the frozen overlay
    /// anchor — whenever ANY of:
    ///  - not in composed-WPT capture (dark-stage 327 protection);
    ///  - the container is not a multicol container (§2);
    ///  - NO spanner precedes the child among its siblings (scope: the
    ///    wave-21 defect is the POST-SPANNER static position; the
    ///    pre-spanner anchor already coincides with the box origin for
    ///    this corpus);
    ///  - a preceding/segment sibling's height is not statically
    ///    resolvable (logOnce — no silent guess);
    ///  - the child needs a column x-offset but the container's content
    ///    width is indefinite (no §3 used geometry to derive x from).
    ///
    /// - Parameters:
    ///   - columns: the container's typed multicol config.
    ///   - siblings: ALL the container's children in SOURCE order (the
    ///     static position is a flow question — CSS `order` does not
    ///     apply to block flow).
    ///   - childId: the abspos child being anchored.
    ///   - contentWidthPx: the container's content-box width (nil =
    ///     indefinite), the §3 used-column basis for the x offset.
    ///   - gapPx: the used column-gap (the caller's multicolUsedGapPx
    ///     lane, so x agrees with the layout's slots by construction).
    ///   - ctx: the container's spacing context for height resolution.
    ///   - wptCaptureMode: composed-WPT capture flag (see above).
    static func staticOffset(columns: ColumnsConfig?,
                             siblings: [IRComponent],
                             childId: String,
                             contentWidthPx: CGFloat?,
                             gapPx: CGFloat,
                             ctx: SpacingContext,
                             wptCaptureMode: Bool) -> CGSize {
        // Dark-stage 327 protection + §2 multicol gate.
        guard wptCaptureMode, columns?.isMulticolContainer == true else { return .zero }
        // The child must actually be one of the container's children.
        guard let childIndex = siblings.firstIndex(where: { $0.id == childId })
        else { return .zero }
        // Role classification through the SHARED twin classifier.
        let roles = MulticolSpannerFlow.rolesFor(children: siblings)
        // Scope gate: only the post-spanner static position is computed
        // here (see the doc above) — no preceding spanner → identity.
        guard roles[..<childIndex].contains(.spanner) else { return .zero }
        // Statically resolve every sibling's block-size: spanner/flow
        // heights shape the plan; static (out-of-flow) siblings never
        // consume space, so their heights are irrelevant → 0.
        var children: [MulticolSpannerFlow.Child] = []
        children.reserveCapacity(siblings.count)
        for (index, sibling) in siblings.enumerated() {
            if roles[index] == .static {
                // Out-of-flow: no space taken (css-position-3 §2.1).
                children.append(.init(heightPx: 0, role: .static))
                continue
            }
            // Explicit Height/BlockSize through the same resolver lane as
            // the fragment plan; percent has no basis (auto container
            // height in this corpus) and an auto height is not statically
            // knowable — bail honestly rather than guess.
            let size = SizeExtractor.extract(from: sibling.properties)
            guard let h = SizeApplierResolve.exact(size.height, ctx: ctx,
                                                   parent: 0,
                                                   allowPercent: false) else {
                PropertyTracker.logOnce(
                    key: "multicol-abspos-static-unresolvable",
                    message: "multicol abspos static position: sibling "
                        + "height not statically resolvable — overlay "
                        + "anchor kept")
                return .zero
            }
            children.append(.init(heightPx: Double(h), role: roles[index]))
        }
        // The §3 used column geometry — needed for BOTH the balanced H
        // (count) and the x offset (width). An indefinite content width
        // has no used geometry; N falls back to the declared count so a
        // column-0 anchor (x = 0) can still resolve its y.
        let used: MulticolMath.UsedColumns? = contentWidthPx.flatMap {
            MulticolMath.usedColumns(availableWidthPx: Double($0),
                                     requestedCount: columns?.count,
                                     requestedWidthPx: columns?.widthPx,
                                     gapPx: Double(gapPx))
        }
        // Declared-count fallback (width-driven configs need the used fit;
        // without it — and without a definite width — bail below if x≠0).
        let n = used?.count ?? max(1, columns?.count ?? 1)
        // The SHARED spanner-flow plan (SP-table) — the child's slot IS
        // its static position.
        let plan = MulticolSpannerFlow.plan(children: children, columnCount: n)
        let slot = plan.slots[childIndex]
        // Column 0 anchors need no inline geometry; a column > 0 anchor
        // without used geometry cannot place x — keep the frozen anchor
        // (logOnce: this is a real approximation, not a spec identity).
        guard slot.columnIndex == 0 || used != nil else {
            PropertyTracker.logOnce(
                key: "multicol-abspos-static-no-width",
                message: "multicol abspos static position: indefinite "
                    + "container width — overlay anchor kept")
            return .zero
        }
        // x = i·(W+G) — the same inline-origin convention as every column
        // consumer (MulticolGreedyLayout, FragmentGeometry).
        let x = CGFloat(slot.columnIndex) * (CGFloat(used?.widthPx ?? 0) + gapPx)
        // y = the SP-table block offset from the content-box top.
        return CGSize(width: x, height: CGFloat(slot.yPx))
    }
}
