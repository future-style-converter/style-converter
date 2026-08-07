//
//  ComposedRootInlineFlow.swift
//  Screenshot — wave 34, lane H (H1).
//
//  The composed WPT canvas's DOCUMENT-ROOT row flow. Byte-parallel twin of
//  apps/android-harness .../screenshot/ComposedRootInlineFlow.kt — same
//  names, same two entry points, same enumerated blast radius.
//
//  ## The measured defect
//  CSS2/abspos/static-inside-inline-block is four boxes of HTML:
//
//    <p>Test passes if there is a filled green square and no red.</p>
//    <div style="display:inline-block; width:100px; height:100px"></div>
//    <div style="display:inline-block; width:100px; height:100px;
//                background:red">
//      <div style="position:absolute; width:100px; height:100px;
//                  background:green"></div>
//    </div>
//    <div style="display:inline-block; width:100px; height:100px"></div>
//
//  The three inline-blocks belong on ONE line box (CSS 2.1 §9.4.2) and the
//  green abspos box paints at its static position inside the red one, so
//  the browser-ref puts green at image (121, 88) — 16px frame + 100px + the
//  4.5px collapsed space, and 16 + 16 + a 40px two-line `<p>` + 16.
//
//  Wave 33 (lane C) built the runtime side of exactly this packing
//  (`InlineBlockAtom` + the wave-20 `InlineAtomFlow`) and then MEASURED that
//  it could not reach this test: its three boxes are document ROOTS, and
//  ComposedCaptureCanvas stacks roots in the HARNESS's own VStack, which is
//  not a runtime block box and never enters the block child loop. The frozen
//  wave33-final scores were iOS 0.9088 / Android 0.8965 against a web that
//  renders it natively at 0.9803.
//
//  ## What this file is
//  The VStack's segment walk, and nothing else. Every RULE — which roots are
//  atoms, where the runs are, how a run packs — comes from the runtime's
//  public `InlineBlockAtom` root facade (`rootBox` / `rootSegments` /
//  `rootRowPlan`), which is the same B1–B7 gate table and the same §9.4.2
//  packer the nested lane uses. This file only:
//    • reads the per-root wire facts the facade needs (text, runs, and the
//      document's body-root line-height for B7), and
//    • adapts the pure plan to a SwiftUI `Layout` — the exact measure/place
//      shape `runtimes/swiftui/.../Renderer/InlineAtomBlockLayout.swift`
//      uses for the nested case, so a root row and a nested row cannot
//      drift.
//
//  ## Blast radius, enumerated before the change
//  Replaying the facade over all 324 frozen wave33-final per-test IRs at the
//  document-ROOT level yields exactly two tests:
//    • CSS2/abspos/static-inside-inline-block          — one 3-box run
//    • css-anchor-position/anchor-center-overflow-005  — two 4-box runs
//      (admitted only once wave-34 H2 made B5 box-sizing-aware; its boxes
//      are `box-sizing: border-box` with a 5px border)
//  Every other composed section has NO root-level run, so `rootSegments`
//  returns nil there and ComposedCaptureCanvas executes its frozen root
//  `ForEach` verbatim. The dark-stage 327-pair corpus never reaches this
//  canvas at all (it captures per COMPONENT through CaptureCanvas), so those
//  baselines are byte-identical by construction.
//

import SwiftUI
import StyleConverterRuntime

/// Per-root `InlineBlockAtom.RootBox` (nil = "not an inline-block atom"),
/// index-aligned with `roots` — the input `InlineBlockAtom.rootSegments`
/// consumes.
///
/// The three wire facts the facade needs:
///  - `text` / `meta.runs` — B6: own line-box content moves an
///    inline-block's baseline off its bottom margin edge (CSS 2.1 §10.8.1),
///    so such a box leaves the lane;
///  - the document's `body-root` `line-height` — B7: the packer's 16/4 strut
///    pins are solved for the ref's INJECTED body line box (the `-lh-` term
///    in the frozen ref-corpus id), and an author override invalidates them.
///    The body-root lookup is the same `meta.role == "body-root"` read
///    `canvasBackground` / `resolvedPadding` already do, so the three
///    resolvers cannot disagree about which root represents the document
///    body.
///
/// Pure over the decoded IR — unit-pinned in ComposedRootInlineFlowTests.
func composedRootInlineBoxes(_ roots: [IRComponent]) -> [InlineBlockAtom.RootBox?] {
    // B7's container read: does the document's body declare its own
    // line-height? Computed once — it is a property of the DOCUMENT, not of
    // any individual root.
    let bodyDeclaresLineHeight = roots
        .first(where: { $0.meta?.role == "body-root" })?
        .properties.contains(where: { $0.type == "LineHeight" }) ?? false
    return roots.map { root in
        InlineBlockAtom.rootBox(
            properties: root.properties,
            // B6 — the component's own text is a statically visible line box.
            hasOwnText: root.text?.isEmpty == false,
            // B6 — so is an ordered inline-run list (wave-32 lane R).
            hasOwnRuns: root.meta?.runs?.isEmpty == false,
            containerDeclaresLineHeight: bodyDeclaresLineHeight
        )
    }
}

/// Lay one run of inline-block ROOTS out as §9.4.2 rows.
///
/// Byte-for-byte the measure/place shape of the runtime's
/// `InlineAtomBlockLayout` (wave-20 lane W3), with the one simplification
/// the root family allows: every member's border box is DEFINITE (B2
/// admitted it on exactly that condition), so both axes are exact proposals
/// and no member ever measures free — which also means the size and place
/// passes derive the identical plan from pure inputs.
///
///  - measurement: `boxes[i]` becomes an exact `ProposedViewSize`, and the
///    BOX is the facade's promise even if the view declines the proposal
///    (the same override `InlineAtomBlockLayout` applies to widget replicas);
///  - packing: `InlineBlockAtom.rootRowPlan` at the incoming proposal width
///    — the VStack's CONTENT width, 358pt on the composed canvas, which is
///    what makes 3×100 + 2×4.5 = 309 fit on one row and a 4th wrap;
///  - reporting: the run's own extent, so the next root stacks below the
///    LAST row (and overflowing ink still paints unclipped, the same
///    contract the nested adapter pins).
///
/// CSS px == pt at the ImageRenderer capture scale (the composed canvas is
/// 390pt wide and its PNG is 390px wide), which is why the gap is passed
/// through verbatim — the same thing `InlineAtomBlockLayout` does.
@available(iOS 16.0, *)
struct ComposedRootInlineRow: Layout {
    /// The run members' definite border boxes, index-aligned with the
    /// subviews the caller builds from the same segment indices.
    let boxes: [InlineBlockAtom.RootBox]

    /// Per-member exact proposals — one place builds them so the size and
    /// place passes cannot disagree.
    private func proposals() -> [ProposedViewSize] {
        boxes.map { ProposedViewSize(width: CGFloat($0.widthPx),
                                     height: CGFloat($0.heightPx)) }
    }

    /// The pure §9.4.2 plan for this proposal. `boxes` (not the measured
    /// sizes) drive it: B2 already made every member definite, so a view
    /// that declines its proposal must still be PLACED in the promised box.
    private func plan(for proposal: ProposedViewSize) -> InlineBlockAtom.RootRowPlan {
        InlineBlockAtom.rootRowPlan(
            widthsPx: boxes.map(\.widthPx),
            heightsPx: boxes.map(\.heightPx),
            // The VStack's content width drives the wrap; an unbounded
            // proposal simply never wraps.
            availableWidthPx: proposal.width.map { Double($0) } ?? .infinity,
            // The collapsed source white-space between inline siblings.
            gapPx: InlineBlockAtom.rootAtomGapPx
        )
    }

    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        // Empty run → zero, like an empty VStack slot.
        guard !subviews.isEmpty else { return .zero }
        let p = plan(for: proposal)
        return CGSize(width: p.widthPx, height: p.heightPx)
    }

    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout ()) {
        // Nothing to place for an empty run.
        guard !subviews.isEmpty else { return }
        // Re-derive the identical plan (pure inputs cannot drift).
        let p = plan(for: proposal)
        let props = proposals()
        for i in subviews.indices where i < p.xPx.count {
            // 104.5 → the ref's image x = 16 + 104.5 = 120.5, which the
            // browser rasterises as the 121 column the diagnosis probe
            // measured; SwiftUI keeps the fractional origin and rasterises
            // it the same way, so no rounding is applied here.
            subviews[i].place(
                at: CGPoint(x: bounds.minX + CGFloat(p.xPx[i]),
                            y: bounds.minY + CGFloat(p.yPx[i])),
                anchor: .topLeading,
                proposal: i < props.count ? props[i] : .unspecified)
        }
    }
}
