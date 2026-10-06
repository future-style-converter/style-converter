//
//  MulticolSpannerContainingBlock.swift
//  StyleConverterRuntime — wave 52 (lane L3 · failure-ink, fix F3).
//
//  The CONTAINING-BLOCK CHAIN across a column spanner — the Swift port of
//  Compose columns/MulticolSpannerContainingBlock.kt (wave 49, lane A7),
//  pure functions only; the same S1–S4 pin table runs on both natives
//  (MulticolSpannerContainingBlockTest.kt / …Tests.swift).
//
//  ## The measured defect (iOS half — Android was fixed in wave 49)
//  `tools/titan/runs/wave51-fix/sections/css-multicol/ios-screenshots/
//   wpt__css-multicol__abspos-containing-block-outside-spanner.png`
//  paints the test's RED 100×100 probe UNCOVERED at the canvas top-left
//  (ios f 0.9553, colorFailed, 20 000 red px) while the frozen ref paints
//  two GREEN squares at the initial containing block's top-left and
//  bottom-right corners and no red at all. iOS put the green square 100px
//  lower, under the paragraph: its `top:0` resolved against the
//  `position: relative` column item, and the bottom-right twin left the
//  canvas. Android passes (P 0.999) since wave 49; web passes (P 1.0000).
//
//  ## The document, and what CSS says about it
//  ```html
//  <div style="columns:3; column-gap:1em; width:20em;">      <!-- multicol -->
//    <div style="position:relative;">                        <!-- column item -->
//      <div style="column-span:all; height:50px;">           <!-- SPANNER -->
//        <div style="position:absolute; top:0; left:0;   …green…"></div>
//        <div style="position:absolute; bottom:0; right:0; …green…"></div>
//  ```
//  css-multicol-1 §6 ("Spanning Columns"): a `column-span: all` box is taken
//  OUT of its ancestors' block flow inside the multi-column container and
//  laid out as a full-width block of the MULTICOL CONTAINER; the ancestors
//  it was nested in fragment AROUND it. They therefore do not geometrically
//  contain the spanner, and CSS 2.1 §10.1's "nearest positioned ancestor"
//  search for the spanner's absolutely positioned descendants must continue
//  from the multi-column container outward — never stopping at a
//  `position: relative` box that lives in the column flow. Here nothing
//  outside the multicol is positioned, so the green boxes fall back to the
//  INITIAL containing block (§10.1 rule 4) and land on the canvas corners,
//  exactly covering the red probes — what the committed ref shows.
//
//  ## Where the iOS runtime consumes it
//  iOS has no per-ancestor positioned chain in ComponentRenderer (each
//  renderer partitions its OWN direct children into flow / overlay), so the
//  one place the chain is walked over the whole tree is `FixedHoist.split`'s
//  descendant strip. That walk threads the two functions below and hoists
//  a spanner's inset abspos descendant to the canvas-root overlay when the
//  RESTARTED chain has no positioned box — the same tree-rewrite mechanism
//  the wave-17 fixed hoist uses, so no per-branch renderer code is needed.
//
//  ## Blast radius, enumerated before the change
//  Census over the 1435 wave51-fix per-test IR docs (tools/titan/results/
//  wave52-failure-ink/census.json, F3): EXACTLY ONE test carries a
//  `ColumnSpan` box with an ABSOLUTE/FIXED descendant — this one. Every
//  other document reads the pre-wave-52 value from every function here.
//

import Foundation

enum MulticolSpannerContainingBlock {

    /// Is this declaration list a `column-span: all` box?
    ///
    /// Decoded through `ColumnsExtractor` — the same reader the columns
    /// family uses — reading the keyword MulticolSpannerFlow.rolesFor
    /// classifies as `.spanner` ("ALL"; converter ColumnSpanSerializer),
    /// so this gate can never disagree with the layout pass.
    static func isSpanner(_ properties: [IRProperty]) -> Bool {
        ColumnsExtractor.extract(from: properties)?
            .rawByType["ColumnSpan"]?.uppercased() == "ALL"
    }

    /// Is this declaration list a multi-column CONTAINER?
    ///
    /// css-multicol-1 §3: a box becomes a multi-column container when
    /// `column-count` or `column-width` is anything but `auto` — the
    /// typed `ColumnsConfig.isMulticolContainer` read, so container and
    /// spanner detection share one wire reader (Compose: MultiColumnExtractor).
    static func isMulticolContainer(_ properties: [IRProperty]) -> Bool {
        ColumnsExtractor.extract(from: properties)?.isMulticolContainer ?? false
    }

    /// CSS 2.1 §10.1: a positioned box (anything but `static`) is the
    /// containing block for its absolute descendants. Twin of Compose
    /// `CanvasRootHoist.establishesContainingBlock` — read through the
    /// shared LayoutExtractor so "positioned" means the same thing here as
    /// in the renderer's own out-of-flow partition. An absent `position`
    /// is the initial `static`.
    static func establishesContainingBlock(_ properties: [IRProperty]) -> Bool {
        (LayoutExtractor.extract(from: properties)?.position ?? .staticPos) != .staticPos
    }

    /// The positioned-ancestor flag this node publishes for its CHILDREN.
    ///
    /// Ordinary nodes keep §10.1's OR-accumulation: a child has a positioned
    /// ancestor if one already existed, or if this node is itself positioned.
    ///
    /// A SPANNER restarts the accumulation from `positionedAtMulticol` — the
    /// value in force at its multi-column container — because css-multicol-1
    /// §6.1 lays the spanner out as a block of that container, so every box
    /// between the container and the spanner is skipped. The spanner's OWN
    /// `position` still counts: it is a real box that really does contain
    /// its descendants.
    ///
    /// `positionedAtMulticol` is nil when there is no multi-column ancestor
    /// at all. `column-span` has no effect outside a multi-column container
    /// (§6.1: `all` spans "the nearest multicol ancestor in the same block
    /// formatting context" — with none there is nothing to span), so a nil
    /// keeps the ordinary OR-accumulation and this rule stays inert.
    static func childPositionedAncestor(ancestorPositioned: Bool,
                                        positionedAtMulticol: Bool?,
                                        properties: [IRProperty]) -> Bool {
        // The node's own contribution — identical in both branches, because
        // a spanner is skipped OVER, never skipped AS WELL.
        let own = establishesContainingBlock(properties)
        // The spanner branch: restart from the multicol container's context.
        if let atMulticol = positionedAtMulticol, isSpanner(properties) {
            return atMulticol || own
        }
        // Everything else: the frozen wave-17 rule, byte-for-byte.
        return ancestorPositioned || own
    }

    /// The "positioned-ancestor state at the multi-column container" this
    /// node publishes for its CHILDREN.
    ///
    /// At a multi-column container it becomes `childPositionedAncestor` —
    /// the chain INCLUDING the container itself, which really is an ancestor
    /// of any spanner inside it. Everywhere else it passes through
    /// unchanged, so a spanner nested several levels down still sees its
    /// own container's value. Nested multicols resolve to the innermost
    /// one, which is the container a `column-span: all` spans (§6.1).
    static func childPositionedAtMulticol(positionedAtMulticol: Bool?,
                                          childPositionedAncestor: Bool,
                                          properties: [IRProperty]) -> Bool? {
        isMulticolContainer(properties) ? childPositionedAncestor : positionedAtMulticol
    }

    /// KNOWN LIMIT, stated rather than hidden (no silent fallthrough).
    ///
    /// The TRANSFORM half of the containing-block channel
    /// (`TransformContainingBlock`, FixedHoist's `hasTransformedAncestor`)
    /// is deliberately NOT restarted at a spanner — the same §6.1 argument
    /// would apply to a transformed ancestor inside the column flow, but no
    /// test in the 30 frozen sections carries that shape (the census above
    /// found exactly one spanner-with-out-of-flow-descendant test, and
    /// nothing on its chain is transformed), so restarting it would be an
    /// unmeasured behaviour change. Mirrors the Compose constant of the
    /// same name so the limit is greppable from code on both natives.
    static let transformChainRestartUnimplemented: Bool = true

    /// Does `child` leave its parent for the canvas-root overlay because
    /// css-multicol-1 §6.1 broke its containing-block chain? The ONE
    /// consumer is `FixedHoist.strippingFixedDescendants`. ALL of:
    ///  - the §6.1 restart has FIRED above it (`underSpanner`) — the only
    ///    path this rule touches; every other nested absolute keeps the
    ///    wave-17 behaviour (FixedHoistTests' EXPECTED-DIVERGENCE row and
    ///    BACKLOG 6(e) are untouched — this is not the ICB clause);
    ///  - it is `position: absolute` (fixed already hoists; relative and
    ///    static are in flow — css-position-3 §2.1);
    ///  - the RESTARTED chain has no positioned box (§10.1 rule 4 → the
    ///    initial containing block). A positioned box OUTSIDE the multicol
    ///    keeps the child in the tree, rendered by its direct parent's
    ///    overlay — the pre-wave-52 approximation, logged once under
    ///    `multicol-spanner-abspos-outer-cb`, no corpus witness (census F3:
    ///    exactly one spanner-with-abspos document);
    ///  - no transformed ancestor (css-transforms-1 §3 veto, as for fixed);
    ///  - it carries an inset — a no-inset absolute sits at its STATIC
    ///    position inside the spanner (css-position-3 §3.1), the same
    ///    exemption `FixedHoist.rendersInFlowAsStaticPosition` gives roots.
    static func hoistsToInitialContainingBlock(_ child: IRComponent,
                                               underSpanner: Bool,
                                               hasPositionedAncestor: Bool,
                                               hasTransformedAncestor: Bool) -> Bool {
        // Spanner path only, no transform containing block.
        guard underSpanner, !hasTransformedAncestor else { return false }
        // Absolute only — the shared LayoutExtractor read (one wire decoder).
        guard LayoutExtractor.extract(from: child.properties)?.position == .absolute else { return false }
        // Inset-anchored only (the static-position exemption above).
        guard FixedHoist.hasAnyInset(child) else { return false }
        // A positioned box OUTSIDE the multicol is this child's CSS 2.1 §10.1
        // containing block, which its parent's overlay does NOT model — name
        // the approximation once (no-silent-fallthrough; census F3: no corpus
        // witness) and keep the pre-wave-52 render.
        if hasPositionedAncestor {
            PropertyTracker.logOnce(
                key: "multicol-spanner-abspos-outer-cb",
                message: "multicol spanner: abspos whose containing block is a "
                    + "positioned box outside the multicol — parent overlay kept "
                    + "(approximation, CSS 2.1 §10.1 not modelled)")
            return false
        }
        // Restarted chain empty → the initial containing block (§10.1 rule 4).
        return true
    }
}
