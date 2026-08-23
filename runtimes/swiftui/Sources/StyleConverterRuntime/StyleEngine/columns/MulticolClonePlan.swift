//
//  MulticolClonePlan.swift
//  StyleEngine/columns — wave-46 lane Y3.
//
//  The css-break-3 §5.2 `box-decoration-break: clone` branch of
//  ColumnsApplier.fragmentPlan, split out (the MulticolFloatStripPlan
//  precedent) so ColumnsApplier.swift keeps its size. Builds the
//  FragmentPlan whose fragments come from the clone K-table
//  (MulticolCloneGeometry) and whose per-fragment child copies the
//  renderer's fragment row re-renders at each fragment's size.
//
//  THE RE-RENDER TRICK (why iOS is exact): the renderer composes one
//  ComponentHost per fragment (ComponentRenderer.multicolFragmentRow), so
//  a child with no content of its own — nothing but decoration — can be
//  handed to each fragment RE-DECLARED at that fragment's block-size:
//  borders at its edges, corners rounded for THAT box, the background
//  positioned in THAT box (WPT borders-008: three 100px circles;
//  background-image-007: one cat per column). A SHORT last fragment is
//  simply the child declared shorter — exact, where the Compose twin
//  (single measure) approximates with two clipped bands.
//

// CoreGraphics for CGFloat geometry; Foundation for the tracker.
import CoreGraphics
import Foundation

extension ColumnsApplier {

    /// The clone plan for a sole leaf clone child, or nil when the clone
    /// branch does not engage — every nil but the two silent ones below
    /// (multi-child: the slice row logs that bail itself; a FITTING clone
    /// box: identity, never a fallthrough) is logged once (repo
    /// no-silent-fallthrough rule) and the caller continues into slice.
    ///
    /// - Parameters:
    ///   - childDeclaredBlockSizePx: C as DECLARED (the `Height`/`BlockSize`
    ///     slot, resolved by the caller) — content under an effective
    ///     `box-sizing: content-box`, border-box otherwise.
    ///   - columnBlockSizePx: H — the fragmentainer block-size.
    ///   - used: the §3 used column geometry (count + width).
    ///   - gapPx: G — the used column-gap.
    ///   - childProperties: the child's IR list (bands resolve from it).
    ///   - childSize: the child's SizeConfig (its `box-sizing` keyword).
    ///   - siblingCount: in-flow child count — clone is sole-child only.
    ///   - childIsLeaf: no IR children / text / generated content.
    ///   - wptCaptureMode: resolves the UNSET box-sizing slot (content-box
    ///     under WPT capture — SizeApplierMath.effectiveBoxSizing).
    static func clonePlan(childDeclaredBlockSizePx: CGFloat,
                          columnBlockSizePx: CGFloat,
                          used: MulticolMath.UsedColumns,
                          gapPx: CGFloat,
                          childProperties: [IRProperty],
                          childSize: SizeConfig,
                          siblingCount: Int,
                          childIsLeaf: Bool,
                          wptCaptureMode: Bool) -> FragmentPlan? {
        // Dark-stage 327 protection (the wave-19/21 policy every later
        // multicol pass follows, and the Compose twin's effective gate —
        // its ChildSpecs are nulled outside capture): the clone branch is
        // composed-WPT capture only. Not a fallthrough — the frozen corpus
        // has no multicol clone fixture, so its slice render is the pinned
        // one (no log).
        guard wptCaptureMode else { return nil }
        // Sole-child family only — the slice row's own multi-child gate
        // logs this bail (no double log here).
        guard siblingCount == 1 else { return nil }
        // The content gate: a clone child WITH content needs per-fragment
        // RE-FLOW (its content must advance across fragments while its
        // decoration repeats) — re-declaring the box shorter would cut
        // the content instead. Not built; slice replay kept, logged.
        // (The corpus' box-decoration-break-clone-001/002 shape — green on
        // both natives under slice today, so the bail also protects them.)
        guard childIsLeaf else {
            PropertyTracker.logOnce(
                key: "multicol-clone-content-child",
                message: "box-decoration-break: clone on a content-bearing "
                    + "child — per-fragment re-flow not built, slice replay kept")
            return nil
        }
        // Bands that did not resolve statically (em/%/calc padding) —
        // honest bail, slice kept.
        guard let bands = MulticolCloneDecoration.bands(for: childProperties) else {
            PropertyTracker.logOnce(
                key: "multicol-clone-band-unresolved",
                message: "box-decoration-break: clone with a non-px decoration "
                    + "band — slice replay kept")
            return nil
        }
        // The child's effective box-sizing decides what the declared slot
        // means (css-sizing-3 §3): under content-box the border box is
        // declared + bands; under border-box the declared size IS the
        // border box. Same tri-state the SizeApplier paints with.
        let contentBox = SizeApplierMath.effectiveBoxSizing(
            declared: childSize.boxSizing, wptCaptureMode: wptCaptureMode) == .contentBox
        let borderBoxPx = contentBox
            ? childDeclaredBlockSizePx + CGFloat(bands.totalPx)
            : childDeclaredBlockSizePx
        // A FITTING clone box never reaches a break point (css-break-3 §4)
        // — identity, the same S2 rule as slice (silent: correct render).
        guard borderBoxPx > columnBlockSizePx else { return nil }
        // The pure K-table: nil when the bands alone fill the fragmentainer
        // (no content could ever advance) — logged, slice kept.
        guard let frags = MulticolCloneGeometry.cloneFragments(
                childBlockSize: Double(borderBoxPx),
                columnBlockSize: Double(columnBlockSizePx),
                bands: bands,
                columnWidth: used.widthPx,
                gapPx: Double(gapPx),
                columnCount: used.count) else {
            PropertyTracker.logOnce(
                key: "multicol-clone-no-capacity",
                message: "box-decoration-break: clone bands fill the whole column "
                    + "block-size — slice replay kept")
            return nil
        }
        // Per-fragment DECLARED heights: the fragment's border box minus
        // the bands it re-spends (content-box), or verbatim (border-box);
        // floored at 0 like every used size (css-sizing-3 §3).
        let declared = frags.map { frag -> CGFloat in
            let box = CGFloat(frag.boxBlockSizePx)
            return max(0, contentBox ? box - CGFloat(bands.totalPx) : box)
        }
        return FragmentPlan(fragments: frags.map(\.fragment),
                            columnWidthPx: CGFloat(used.widthPx),
                            columnBlockSizePx: columnBlockSizePx,
                            gapPx: gapPx,
                            cloneDeclaredHeightsPx: declared)
    }
}
