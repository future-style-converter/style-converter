//
//  FixedHoist.swift
//  StyleConverterRuntime — wave 17 (the out-of-flow contract, lane IOS).
//
//  css-position-3 §3.1: the containing block of a `position: fixed` box
//  is the VIEWPORT (for our capture surfaces: the unpadded canvas), and
//  the containing block of a root-level `position: absolute` box with no
//  positioned ancestor is the INITIAL containing block — measured on web
//  (the wave-17 pixel evidence), that is the SAME unpadded canvas origin:
//  an absolute root with `left: 100` lands at canvas (100, 0), never at
//  (16 + 100, 16 + flow-y).
//
//  The diagnosed wave-17 bug (one shared mechanism, native parity 1.000):
//  out-of-flow boxes rendered IN their parent's flow slot and then added
//  the inset as a delta — natural-flow position + offset — so
//    • a root-level absolute/fixed box kept its VStack slot (it reserved
//      flow space AND anchored inside the 16px canvas pad), and
//    • a fixed child of a positioned parent anchored at the PARENT's
//      padding box (the .absolute machinery) instead of the viewport —
//      compounding to parent-origin + inset.
//  Insets on out-of-flow boxes are ABSOLUTE anchors against their
//  containing block, not deltas (css-position-3 §3.5), and out-of-flow
//  boxes leave flow entirely — no space reserved (§2.1).
//
//  The fix is a HOIST, not a new offset formula: because SwiftUI resolves
//  our inset offsets against the nearest anchoring ZStack corner (the
//  wave-1/8 machinery), the containing-block change is implemented by
//  MOUNTING the box at the right corner. `split` statically rewrites a
//  document's root list:
//    • position:fixed DESCENDANTS at ANY depth are stripped from their
//      parent's children and hoisted (F1 — viewport containing block),
//      UNLESS an ancestor with a used transform has taken that containing
//      block over (wave 35, lane B1 — css-transforms-2 §6; the rule table
//      is StyleEngine/layout/position/TransformContainingBlock.swift);
//    • out-of-flow ROOTS (absolute + fixed) are hoisted whole (F2 — the
//      initial containing block IS the canvas for a root-level box);
//    • everything else stays byte-identical, so nested absolute boxes
//      keep the wave-8/9 positioned-ancestor padding-box machinery.
//  The canvas mounts the hoisted list in a `FixedHoistOverlay` attached
//  ABOVE its padding (the unpadded origin) and OVER its content — fixed
//  boxes paint above in-flow content (CSS 2.1 Appendix E step 8), with
//  sibling order/z-index resolved by the existing PositionApplier
//  machinery inside the overlay's ZStack.
//
//  ONLY hoist-aware canvases call in (today: the composed WPT canvas —
//  apps/ios-harness ComposedCaptureCanvas). The per-component dark-stage
//  canvas and the product renderer never call `split`, so the 327
//  committed baselines and every SDUI render stay byte-identical — the
//  wave-1 out-of-flow ROOT card treatment (CaptureCanvas) and the wave-8
//  paint-order overlay are extended by this file, not replaced.
//

import SwiftUI

// public: hoist-aware canvases (the harness's ComposedCaptureCanvas, any
// SDUI shell that owns a full surface) split their root list and mount
// the hoisted half in a FixedHoistOverlay; tests pin the pure transform.
public enum FixedHoist {

    /// True when a component declares `position: fixed` — the F1 class
    /// that hoists from ANY depth (its containing block is always the
    /// viewport, css-position-3 §3.1, regardless of ancestors).
    /// Extraction mirrors ComponentRenderer.isOutOfFlow (one classifier
    /// engine — LayoutExtractor — so the two can never disagree).
    static func isFixed(_ component: IRComponent) -> Bool {
        LayoutExtractor.extract(from: component.properties)?.position == .fixed
    }

    /// Wave 18 (RC1) — does the declaration list carry ANY inset that
    /// anchors the box to its containing block? Read through the SAME
    /// LayoutExtractor lane the live PositionApplier consumes (one wire
    /// decoder — PositionExtractor's InsetRect, where an `auto` or absent
    /// side is nil and logical InsetInline/InsetBlock longhands are
    /// already folded to physical per the LTR horizontal-tb
    /// normalization), so "has an inset" can never disagree with the
    /// offset the applier will paint. Twin of Compose
    /// CanvasRootHoist.hasAnyInset — the two rule tables must match.
    static func hasAnyInset(_ component: IRComponent) -> Bool {
        guard let inset = LayoutExtractor.extract(from: component.properties)?.inset
        else { return false }
        // Any non-nil side = at least one anchoring inset.
        return inset.top != nil || inset.right != nil
            || inset.bottom != nil || inset.left != nil
    }

    /// Wave 18 (RC1) — the static-position class the hoist carves out:
    /// an ABSOLUTE box with NO inset on either axis sits at its STATIC
    /// POSITION (css-position-3 §3.1: where it would have been in flow) —
    /// the wave-17 canvas-origin hoist painted css-sizing abspos-001/002's
    /// square at (0,0) over the paragraph. Such a ROOT stays in the flow
    /// stack (split keeps it) and the canvas wraps it in
    /// [StaticPositionAnchor] so it paints at its slot origin while
    /// reserving NO flow space (§2.1 — the S5 zero-report, iOS edition).
    /// FIXED keeps the wave-17 always-hoist behavior (its containing
    /// block IS the viewport, and every wave-17 css-position green rides
    /// the canvas anchor). public: split's callers (the composed canvas)
    /// use the same classifier to attach the anchor — one decision, two
    /// consumers, mirroring the Compose harness's shouldHoistToCanvasRoot
    /// gap-injection reuse.
    /// `hasPositionedAncestor` mirrors the Compose twin's parameter
    /// (CanvasRootHoist.rendersInFlowAsStaticPosition): an absolute box
    /// UNDER a positioned ancestor is never in this class — it belongs to
    /// the wave-8/9 positioned-children machinery even with all-auto
    /// insets (its static position is inside that ancestor, not a canvas
    /// slot). Defaulted false because every production call site passes a
    /// document ROOT (split + the composed canvas), which by definition
    /// has no ancestor — the parameter exists so the cross-native rule
    /// tables stay byte-identical for NON-root queries too (the wave-18
    /// skeptic probe caught the two natives disagreeing on the
    /// css-sizing fit-content-percentage children without it).
    public static func rendersInFlowAsStaticPosition(
        _ component: IRComponent,
        hasPositionedAncestor: Bool = false
    ) -> Bool {
        LayoutExtractor.extract(from: component.properties)?.position == .absolute
            && !hasPositionedAncestor
            && !hasAnyInset(component)
    }

    /// Split a document's ROOT list into the in-flow half (rendered in
    /// the canvas's padded flow stack) and the hoisted half (mounted in
    /// the canvas-root FixedHoistOverlay at the unpadded origin).
    ///
    /// Hoisted, in document pre-order (paint tie-break = tree order,
    /// CSS 2.1 Appendix E within step 8):
    ///   • every out-of-flow ROOT (absolute + fixed — both anchor at the
    ///     unpadded canvas per the wave-17 web pixel evidence), itself
    ///     first stripped of its own fixed descendants;
    ///   • every `position: fixed` DESCENDANT of any root, at any depth,
    ///     EXCEPT one whose containing block a transformed ancestor has
    ///     taken over (wave 35, lane B1 — css-transforms-1 §3 /
    ///     css-transforms-2 §6; see `strippingFixedDescendants`).
    /// Nested ABSOLUTE descendants are never touched — they keep the
    /// wave-8/9 positioned-ancestor padding-box containing block.
    ///
    /// ── PARITY NOTE, measured (wave 35, lane B1) ────────────────────────
    /// That last clause is where this rule table still DIVERGES from its
    /// Compose twin: `CanvasRootHoist.shouldHoistToCanvasRoot` hoists a
    /// nested inset-anchored ABSOLUTE box to the initial containing block
    /// when no positioned (and now no transformed) ancestor exists, while
    /// `split` leaves every nested absolute where it is.
    ///
    /// Chromium says COMPOSE is right in the abstract — probe A in
    /// _diag35/laneB1/probe-chromium.mjs nests an inset absolute two levels
    /// under un-positioned wrappers and Chromium anchors it at the ICB
    /// (20,10), not at the parent. The clause is nevertheless NOT adopted
    /// here, and the reason is measured rather than aesthetic: the IR's
    /// positioned-ancestor chain is LOSSY. In the six frozen tests that
    /// carry this shape (scan-oof2.mjs), four are css-writing-modes
    /// available-size-00x, whose `body > div { position: relative }` never
    /// reaches the wire at all — the extractor drops the descendant-combinator
    /// rule, so the IR claims "no positioned ancestor" for a box that has
    /// one. iOS's conservative clause renders them correctly by accident and
    /// PASSES all four; Compose's spec-correct clause hoists them to the
    /// canvas corner and FAILS available-size-001/012 at 0.8998. Adopting
    /// the ICB clause here would trade four green cells for zero.
    /// The defect to fix is therefore the extractor's lost `position:
    /// relative`, not this table; until it is fixed the divergence is
    /// deliberate, pinned in FixedHoistTests (the parity matrix marks this
    /// one row EXPECTED-DIVERGENT) and named as a follow-up.
    public static func split(roots: [IRComponent])
        -> (flow: [IRComponent], hoisted: [IRComponent]) {
        // Accumulators preserve document order across both halves.
        var flow: [IRComponent] = []
        var hoisted: [IRComponent] = []
        for root in roots {
            // Fixed descendants strip out of EVERY root (in-flow or not):
            // a fixed box inside a hoisted absolute root still anchors at
            // the viewport, not at that root's padding box — UNLESS a
            // transformed ancestor has taken the containing block over
            // (wave 35, lane B1; see strippingFixedDescendants). A ROOT has
            // no ancestors, so the walk starts with the flag clear and the
            // root's OWN transform (if any) is folded in one level down.
            let stripped = strippingFixedDescendants(root, hasTransformedAncestor: false)
            if ComponentRenderer.isOutOfFlow(root)
                && !rendersInFlowAsStaticPosition(root) {
                // F2/F1 root: the whole box leaves the flow stack — no
                // space reserved (§2.1), unpadded-canvas anchor. The root
                // itself paints before its own hoisted descendants
                // (pre-order = the browser's tree paint order).
                // Wave 18 (RC1): an ABSOLUTE root with NO inset is exempt —
                // css-position-3 §3.1 gives it its STATIC position, so it
                // stays in the flow stack behind StaticPositionAnchor
                // instead of anchoring at the canvas origin (every wave-17
                // css-position hoist case carries an inset, so this branch
                // is strictly additive there). Mixed-axis (one inset only)
                // still hoists — the auto axis approximates its static
                // position with the canvas origin (documented, pinned).
                hoisted.append(stripped.kept)
            } else {
                // In-flow root: keeps its flow slot, minus any fixed
                // descendants — the flow stack reserves NO space for
                // them (§2.1; pin S5: the next sibling starts where the
                // fixed box would have been).
                flow.append(stripped.kept)
            }
            // Fixed descendants follow their root in document order.
            hoisted.append(contentsOf: stripped.hoisted)
        }
        return (flow, hoisted)
    }

    /// Wave 19 (RC-A5b) — the CSS 2.1 Appendix E paint SPLIT of the
    /// hoisted list: a hoisted box with NEGATIVE z-index paints in step 3
    /// of the root stacking context — BEHIND all in-flow canvas content —
    /// while everything else keeps the wave-17 step-8 overlay (above).
    ///
    /// The wave-17 single `.overlay` mount put the whole hoisted list
    /// above the flow stack, and SwiftUI's `.zIndex` only reorders
    /// SIBLINGS of one ZStack — it cannot push overlay content behind
    /// the overlay's base. So css-flexbox dynamic-align-self-001's
    /// `z-index:-1` red probe (a hoisted abspos root) painted OVER the
    /// green abspos child living in its flex parent's positioned-children
    /// overlay, exactly covering it (both 100×100 at canvas (16,88)) —
    /// the wave18-final "green never paints" iOS failure. The Compose
    /// harness never had the bug: CanvasRootHoist.Host mounts flow +
    /// hoisted slots in ONE Box, where Modifier.zIndex(-1) already sinks
    /// the red box below the flow child.
    ///
    /// The z read rides the SAME ItemPlacementExtractor lane the
    /// renderer's own negative-z split (ComponentRenderer
    /// .negativeZChildren) consumes — one wire decoder, so the canvas
    /// and the per-ancestor overlay can never classify a box differently.
    /// Hoist-aware canvases mount `behind` as a `.background` layer
    /// (above the canvas background, below flow content — step 3) and
    /// `above` as the existing `.overlay` (step 8); document order is
    /// preserved inside each half (tree-order tie-break within a step).
    public static func paintPartition(_ hoisted: [IRComponent])
        -> (behind: [IRComponent], above: [IRComponent]) {
        // Stable partition — document order survives in both halves.
        var behind: [IRComponent] = []
        var above: [IRComponent] = []
        for c in hoisted {
            // Appendix E: negative z ⇒ step 3 (behind in-flow content);
            // zero/auto/positive ⇒ step 8 (above), ties by tree order.
            if (ItemPlacementExtractor.extract(from: c.properties).paint.zIndex ?? 0) < 0 {
                behind.append(c)
            } else {
                above.append(c)
            }
        }
        return (behind, above)
    }

    /// Recursively remove `position: fixed` descendants from a subtree.
    /// Returns the rebuilt component (identical except for the removed
    /// children — a subtree with no fixed descendants comes back with
    /// its ORIGINAL children array untouched) plus the removed boxes in
    /// document pre-order. The component ITSELF is never classified
    /// here — root-level classification is `split`'s job.
    ///
    /// Wave 35 (lane B1) — `hasTransformedAncestor` is the css-transforms-1
    /// §3 / css-transforms-2 §6 veto: an ancestor with a USED transform
    /// (`TransformContainingBlock`, the byte-parallel twin of Compose's rule
    /// table) is the containing block for FIXED descendants too, so such a
    /// box must NOT be stripped to the viewport overlay. Left in place it
    /// takes the ordinary out-of-flow route — ComponentRenderer's
    /// `outOfFlowChildren` ZStack overlay on its parent — which anchors it at
    /// that parent's padding box, exactly what the browser does (probe C/D/E
    /// in _diag35/laneB1/probe-chromium.mjs). The flag accumulates with OR:
    /// a used transform never un-establishes a containing block.
    ///
    /// Defaulted false so the parameter is additive — every pre-wave-35 call
    /// site (tests included) keeps the wave-17 always-strip behaviour.
    static func strippingFixedDescendants(_ component: IRComponent,
                                          hasTransformedAncestor: Bool = false)
        -> (kept: IRComponent, hoisted: [IRComponent]) {
        // Leaf (nil children): nothing to strip, return verbatim.
        guard let children = component.children else { return (component, []) }
        // Children see a transform-CB ancestor if one already existed OR
        // THIS component establishes one (css-transforms-1 §3). Computed
        // once per level, not per child.
        let childTransformed = hasTransformedAncestor
            || TransformContainingBlock.establishes(component)
        // Walk children in order, recursing FIRST so a fixed grandchild
        // inside a kept child hoists too (any depth).
        var keptChildren: [IRComponent] = []
        var hoisted: [IRComponent] = []
        // Tracks whether any strip actually happened — if not, the
        // original component is returned UNCHANGED (no rebuild), keeping
        // fixed-free trees reference-identical to their input.
        var changed = false
        for child in children {
            if isFixed(child) && !childTransformed {
                // F1: the fixed box leaves its parent entirely — no flow
                // space, no parent-anchored inset basis. Its own subtree
                // is stripped too (a fixed box nested in a fixed box
                // also anchors at the viewport).
                let sub = strippingFixedDescendants(child,
                                                    hasTransformedAncestor: childTransformed)
                hoisted.append(sub.kept)
                hoisted.append(contentsOf: sub.hoisted)
                changed = true
            } else {
                // Kept child — recurse for deeper fixed descendants. This
                // branch now also carries the wave-35 case: a fixed child
                // whose containing block a transformed ancestor claimed
                // (`childTransformed`), which stays in the tree and renders
                // through its parent's out-of-flow overlay.
                let sub = strippingFixedDescendants(child,
                                                    hasTransformedAncestor: childTransformed)
                keptChildren.append(sub.kept)
                hoisted.append(contentsOf: sub.hoisted)
                // Rebuild only if the recursion actually changed it.
                changed = changed || !sub.hoisted.isEmpty
            }
        }
        // Untouched subtree → the original value, byte-identical.
        guard changed else { return (component, []) }
        // Decode contract (IRModels): `children` is nil, never [], when
        // empty — normalize so a parent whose only children were fixed
        // renders exactly like a wire-decoded childless leaf.
        return (replacingChildren(of: component,
                                  with: keptChildren.isEmpty ? nil : keptChildren),
                hoisted)
    }

    /// Rebuild a component with a new children array — every other field
    /// copied verbatim through the internal memberwise init (the same
    /// constructor IRComposer uses to attach slot children).
    private static func replacingChildren(of c: IRComponent,
                                          with children: [IRComponent]?) -> IRComponent {
        IRComponent(id: c.id, name: c.name, properties: c.properties,
                    selectors: c.selectors, media: c.media,
                    children: children, slot: c.slot,
                    text: c.text, pseudos: c.pseudos, meta: c.meta,
                    variables: c.variables)
    }
}

/// The canvas-root mount for the hoisted half of `FixedHoist.split`:
/// a top-leading ZStack the canvas attaches as an `.overlay` on its
/// FULL frame, inset by `canvasFrame` — so each hoisted box's own
/// PositionApplier (the flexible top-leading frame + inset offset,
/// wave-1 machinery) resolves `left`/`top` against the INITIAL
/// CONTAINING BLOCK's corner: exactly the viewport containing block F1
/// requires and the ICB §3.1 gives un-ancestored absolute roots.
///
/// Wave 17 pinned that corner at the canvas's own (0,0) from measured
/// web evidence — correct then, because the ref framed its pages with
/// `:where(body){padding:16px}` and CSS padding on a static body moves
/// in-flow content WITHOUT moving out-of-flow content (so the ref's
/// abspos `left:100` really did land at canvas x=100 while its prose sat
/// at 116 — the ref was internally misaligned with itself). Wave 25
/// CAL-RC1 repaired that by moving the frame into IMAGE space, where a
/// memcpy translates every pixel alike; the ICB corner is therefore the
/// FRAMED content corner now, and `canvasFrame` is that translation.
///
/// Attaching as an overlay also gives Appendix E step 8 for free —
/// hoisted boxes paint ABOVE all in-flow canvas content, with tree
/// order and `.zIndex` (applied per box by PositionApplier) breaking
/// ties inside this ZStack, per the existing z-order machinery.
public struct FixedHoistOverlay: View {
    /// The hoisted boxes, in document pre-order (see FixedHoist.split).
    let components: [IRComponent]

    /// Wave 25 (round 3) — the inset from the attach frame to the
    /// INITIAL CONTAINING BLOCK, i.e. the composed canvas's image-space
    /// frame (`WPTCanvas.canvasFramePx`). Defaulted to 0 so any caller
    /// that mounts on an already-ICB-sized frame — and every pre-wave-25
    /// call site — is byte-identical.
    let canvasFrame: CGFloat

    // public: explicit memberwise init — the synthesized one is
    // internal, so the harness canvas needs it spelled out.
    public init(components: [IRComponent], canvasFrame: CGFloat = 0) {
        self.components = components
        self.canvasFrame = canvasFrame
    }

    // public: View protocol witness on a public type must be public.
    public var body: some View {
        // GeometryReader fills the overlay's proposal and places its child
        // at the top-leading origin — and hands us its OWN size, which IS
        // these boxes' containing block (css-position-3 §3.1: the viewport
        // for fixed; the initial containing block for un-ancestored
        // absolute roots). The `.padding(canvasFrame)` below shrinks that
        // proposal from the framed canvas to the ICB, so ONE modifier moves
        // both the anchor corner and the published containing-block size —
        // they can never disagree.
        GeometryReader { geo in
            // Top-leading ZStack: the corner every PositionApplier
            // anchored-frame offset resolves from (wave-1 contract).
            ZStack(alignment: .topLeading) {
                // Positional identity — hoisted boxes are never
                // reordered, same rationale as the canvas root loop.
                ForEach(Array(components.enumerated()), id: \.offset) { _, comp in
                    // Full engine path: ComponentHost → ComponentRenderer,
                    // whose own PositionApplier applies the inset offset —
                    // the overlay adds NO offset of its own, so the inset
                    // can never be double-counted.
                    ComponentHost(component: comp)
                        // The composed canvas publishes its content-box
                        // width for stacked ROOTS (block fill) — a hoisted
                        // box is out-of-flow and must size to its own
                        // declarations (css-position-3 §2.1), so reset the
                        // channel rather than lean on the renderer's guard.
                        .environment(\.wptBlockFlowFillWidth, nil)
                        // Out-of-flow boxes never margin-collapse (CSS 2.1
                        // §8.3.1 "in-flow" precondition) — same reset the
                        // wave-8 positionedChildren loop applies.
                        .environment(\.marginCollapseOverride, nil)
                        // The containing block IS the canvas: publish its
                        // measured size so percent sizes on hoisted boxes
                        // resolve against the viewport (§3.1), not the
                        // 358px padded root basis the canvas publishes
                        // for in-flow roots.
                        // (geo is the FRAMED ICB — see the padding below.)
                        .environment(\.containingBlockWidth, geo.size.width)
                        .environment(\.containingBlockHeight, geo.size.height)
                    // Honest limitation (documented, not silent): hoisting
                    // is a static tree transform, so a hoisted descendant
                    // inherits text properties from the CANVAS scope, not
                    // its original parent chain (css-cascade-4 would
                    // inherit by tree). None of the wave-17 pins exercise
                    // inherited text on fixed boxes; revisit if a corpus
                    // test does.
                }
            }
        }
        // Wave 25 (round 3) — the frame→ICB inset. Applied OUTSIDE the
        // GeometryReader so it shrinks the reader's proposal: the ZStack's
        // top-leading corner becomes the ICB corner (16,16 on the framed
        // canvas) AND `geo.size` becomes the ICB extent (358 × H−32) that
        // the two containing-block environment keys above publish. Zero
        // frame ⇒ SwiftUI's padding is a no-op layout-wise, so every
        // pre-wave-25 mount is byte-identical.
        .padding(canvasFrame)
    }
}

/// Wave 18 (RC1) — the flow-slot mount for a no-inset ABSOLUTE root the
/// split now KEEPS in flow (see FixedHoist.rendersInFlowAsStaticPosition):
/// the box must paint at its STATIC position (its slot origin — exactly
/// where the flow stack placed it) while reserving NO flow space
/// (css-position-3 §2.1; the S5 zero-report contract, iOS edition — the
/// twin of Compose CanvasRootHoist.zeroFlowAnchor):
///   • `.fixedSize()` measures the subtree at its IDEAL size — the
///     SwiftUI analogue of Compose's unbounded Constraints() measure, so
///     the box is sized by its own declarations alone (an out-of-flow box
///     is sized against its containing block, never squeezed by flow
///     siblings; without it the zero frame below would propose 0×0 and
///     collapse/wrap the content);
///   • the zero `.frame(width: 0, height: 0, alignment: .topLeading)`
///     reports NO extent to the flow stack — the next sibling starts
///     where this box would have (S5) — while the top-leading alignment
///     pins the ink at the slot origin; SwiftUI does not clip children to
///     frames, so the ink overflows exactly like CSS overflow:visible.
/// public: applied by hoist-aware canvases (the composed WPT canvas) on
/// flow roots the classifier marks; the dark-stage canvas and the product
/// renderer never apply it, keeping all committed baselines byte-stable.
public struct StaticPositionAnchor: ViewModifier {
    // public: explicit memberwise init — the synthesized one is internal.
    public init() {}

    // public: ViewModifier witness on a public type must be public.
    public func body(content: Content) -> some View {
        content
            // Ideal-size measure (the unbounded-measure twin) BEFORE the
            // zero frame so the 0×0 proposal never reaches the content.
            .fixedSize()
            // Zero flow report + slot-origin anchor (S5 + static position).
            .frame(width: 0, height: 0, alignment: .topLeading)
    }
}
