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
/// Pure over the decoded IR — unit-pinned in
/// apps/ios-harness/StyleConverterTestTests/ComposedRootInlineFlowTests.swift
/// (retro R10, finding A4#5: that file did not exist when this sentence was
/// written, and nothing anywhere referenced `composedRootInlineBoxes`; it
/// now pins the two victim documents' atoms and runs on their verbatim
/// vendored IR). CAVEAT, stated because the finding was about a false
/// coverage claim: the `StyleConverterTestTests` bundle is not part of any
/// documented sweep — it needs a booted simulator and
/// tools/visual/doc-staleness-check.sh warns about the gap on every run —
/// so the pin that EXECUTES on every wave is its Kotlin twin,
/// apps/android-harness .../screenshot/ComposedRootInlineFlowTest.kt, which
/// reads the same vendored payload.
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
            containerDeclaresLineHeight: bodyDeclaresLineHeight,
            // wave-44 U3b — the delivered-replaced attestation, read by the
            // runtime's own seam (candidacy + DocumentImageRegistry decode)
            // so this harness and the Compose twin cannot disagree about
            // what "delivered" means. Nil (an undelivered/non-replaced
            // root) keeps the wave-34 behavior byte-for-byte.
            replaced: InlineBlockAtom.replacedRootFacts(of: root)
        )
    }
}

/// wave-44 U3a — one root's DECLARED-NEUTRAL stack-plan entry: the same
/// `RootStackMargin` the canvas's rootPlans fold builds, but with the
/// root's declared block margins contributing ZERO (`staticDeclaredEdges:
/// (0, 0)` — declared sides muted, undeclared sides keep their UA
/// default) while the wave-26 hoist band keeps its real value.
///
/// Used for atom roots in two places (see CaptureCanvas's segment walk):
///  - the H3 PROBE — an admitted atom's declared margins now ride its
///    `InlineBlockAtom.RootBox` and are re-expressed by the packer's
///    margin-box fold, so the gap they used to inject must not refuse the
///    run; anything the neutralization does NOT explain (a UA margin, a
///    hoist band) still does;
///  - the RUN-path gap emission — the pad above a run must carry only the
///    PREVIOUS root's contribution (the browser puts the first member's
///    own margin-top INSIDE the line box, §10.8; emitting it in the pad
///    too would double-count it whenever it exceeds the neighbor's bottom
///    margin).
/// Atoms are never hoisted/static-position (B3 refuses out-of-flow), so
/// the plain opaque branch of the rootPlans fold is the only shape here.
/// Twin: apps/android-harness ComposedRootInlineFlow.atomNeutralPlan.
func atomNeutralPlan(_ root: IRComponent) -> UABlockMargin.RootStackMargin {
    // Declared sides contribute 0 (the packer owns them); undeclared keep
    // the UA default, which is what lets a UA-margined atom-shaped root
    // still refuse through the probe.
    let base = UABlockMargin.rootStackMargin(
        tag: root.meta?.sourceTag,
        declaresTop: UABlockMargin.declaresBlockMarginTop(root.properties),
        declaresBottom: UABlockMargin.declaresBlockMarginBottom(root.properties),
        staticDeclaredEdges: (0, 0))
    // The wave-26 hoist band is OUTER spacing the packer does not model —
    // it keeps its real value so a band-carrying run refuses via H3.
    return UABlockMargin.withHoistBand(
        base,
        band: UABlockMargin.composedRootHoistBand(root, uaBlockMargins: true))
}

/// wave-44 U3a — the stacked spacing a segment walk should emit, given
/// which roots' declared block margins moved into the inline packer:
/// `neutralize[i]` marks the roots (always exactly the RUN members, or
/// every atom for the H3 probe) whose plan entries are replaced by
/// `atomNeutralPlan`; everything else keeps its real `plans` entry, so
/// singles and non-atoms collapse exactly as the frozen loop would.
/// Pure — twin of the Compose harness's neutralizedRootGapsPx.
func neutralizedStackedSpacing(
    _ roots: [IRComponent],
    plans: [UABlockMargin.RootStackMargin],
    neutralize: [Bool]
) -> (leading: [CGFloat], trailing: CGFloat) {
    UABlockMargin.stackedSpacing(plans: plans.enumerated().map { i, plan in
        (i < neutralize.count && neutralize[i]) ? atomNeutralPlan(roots[i]) : plan
    })
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

    /// wave-44 U3 (MEASURED on the iPhone 17 Pro sim): the canvas CONTENT
    /// width to wrap at when the incoming proposal carries no usable
    /// width. SwiftUI probes a Layout's ideal size with `.unspecified`
    /// (width nil) and its max with `.infinity` — under either, the old
    /// `?? .infinity` fallback answered the NO-WRAP single-row size, so
    /// box-sizing-007's twenty imgs reported one 124pt row where the real
    /// render wraps into ten (1240pt). The canvas's `fixedSize(vertical:
    /// true)` then kept the 600pt floor and SwiftUI CENTERED the
    /// overflowing VStack — the arm-B capture lost the `<p>` off the top
    /// and five rows off the bottom (iOS 0.7325 vs Android's 0.9844 on
    /// the identical plan). Wrapping at the canvas width is the honest
    /// ideal answer: it is the exact width the placement pass receives.
    /// Wave-34's one-row runs report identical sizes either way, so every
    /// frozen capture is unchanged.
    let fallbackAvailableWidthPx: Double

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
            // The VStack's content width drives the wrap. A nil width
            // (SwiftUI's `.unspecified` ideal probe) or a non-finite one
            // (the `.infinity` max probe) falls back to the canvas content
            // width — see fallbackAvailableWidthPx's MEASURED rationale.
            availableWidthPx: proposal.width.flatMap {
                $0.isFinite ? Double($0) : nil
            } ?? fallbackAvailableWidthPx,
            // The collapsed source white-space between inline siblings.
            gapPx: InlineBlockAtom.rootAtomGapPx,
            // wave-44 U3a — the members' declared margins, packer-owned
            // (the render is margin-stripped): the facade folds them into
            // §10.8.1 margin-box rows and hands back BORDER-box origins.
            // CSS px == pt at the capture scale, so no conversion here.
            marginTopsPx: boxes.map(\.marginTopPx),
            marginRightsPx: boxes.map(\.marginRightPx),
            marginBottomsPx: boxes.map(\.marginBottomPx),
            marginLeftsPx: boxes.map(\.marginLeftPx)
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
