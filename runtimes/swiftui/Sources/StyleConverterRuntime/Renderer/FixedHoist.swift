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
//      parent's children and hoisted (F1 — viewport containing block);
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

    /// Split a document's ROOT list into the in-flow half (rendered in
    /// the canvas's padded flow stack) and the hoisted half (mounted in
    /// the canvas-root FixedHoistOverlay at the unpadded origin).
    ///
    /// Hoisted, in document pre-order (paint tie-break = tree order,
    /// CSS 2.1 Appendix E within step 8):
    ///   • every out-of-flow ROOT (absolute + fixed — both anchor at the
    ///     unpadded canvas per the wave-17 web pixel evidence), itself
    ///     first stripped of its own fixed descendants;
    ///   • every `position: fixed` DESCENDANT of any root, at any depth.
    /// Nested ABSOLUTE descendants are never touched — they keep the
    /// wave-8/9 positioned-ancestor padding-box containing block.
    public static func split(roots: [IRComponent])
        -> (flow: [IRComponent], hoisted: [IRComponent]) {
        // Accumulators preserve document order across both halves.
        var flow: [IRComponent] = []
        var hoisted: [IRComponent] = []
        for root in roots {
            // Fixed descendants strip out of EVERY root (in-flow or not):
            // a fixed box inside a hoisted absolute root still anchors at
            // the viewport, not at that root's padding box.
            let stripped = strippingFixedDescendants(root)
            if ComponentRenderer.isOutOfFlow(root) {
                // F2/F1 root: the whole box leaves the flow stack — no
                // space reserved (§2.1), unpadded-canvas anchor. The root
                // itself paints before its own hoisted descendants
                // (pre-order = the browser's tree paint order).
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

    /// Recursively remove `position: fixed` descendants from a subtree.
    /// Returns the rebuilt component (identical except for the removed
    /// children — a subtree with no fixed descendants comes back with
    /// its ORIGINAL children array untouched) plus the removed boxes in
    /// document pre-order. The component ITSELF is never classified
    /// here — root-level classification is `split`'s job.
    static func strippingFixedDescendants(_ component: IRComponent)
        -> (kept: IRComponent, hoisted: [IRComponent]) {
        // Leaf (nil children): nothing to strip, return verbatim.
        guard let children = component.children else { return (component, []) }
        // Walk children in order, recursing FIRST so a fixed grandchild
        // inside a kept child hoists too (any depth).
        var keptChildren: [IRComponent] = []
        var hoisted: [IRComponent] = []
        // Tracks whether any strip actually happened — if not, the
        // original component is returned UNCHANGED (no rebuild), keeping
        // fixed-free trees reference-identical to their input.
        var changed = false
        for child in children {
            if isFixed(child) {
                // F1: the fixed box leaves its parent entirely — no flow
                // space, no parent-anchored inset basis. Its own subtree
                // is stripped too (a fixed box nested in a fixed box
                // also anchors at the viewport).
                let sub = strippingFixedDescendants(child)
                hoisted.append(sub.kept)
                hoisted.append(contentsOf: sub.hoisted)
                changed = true
            } else {
                // Kept child — recurse for deeper fixed descendants.
                let sub = strippingFixedDescendants(child)
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
/// FULL, UNPADDED frame — so each hoisted box's own PositionApplier
/// (the flexible top-leading frame + inset offset, wave-1 machinery)
/// resolves `left`/`top` against the unpadded canvas origin (0,0):
/// exactly the viewport containing block F1 requires and the initial
/// containing block the wave-17 web evidence pins for absolute roots.
/// Attaching as an overlay also gives Appendix E step 8 for free —
/// hoisted boxes paint ABOVE all in-flow canvas content, with tree
/// order and `.zIndex` (applied per box by PositionApplier) breaking
/// ties inside this ZStack, per the existing z-order machinery.
public struct FixedHoistOverlay: View {
    /// The hoisted boxes, in document pre-order (see FixedHoist.split).
    let components: [IRComponent]

    // public: explicit memberwise init — the synthesized one is
    // internal, so the harness canvas needs it spelled out.
    public init(components: [IRComponent]) {
        self.components = components
    }

    // public: View protocol witness on a public type must be public.
    public var body: some View {
        // GeometryReader fills the overlay's proposal (the unpadded
        // canvas frame) and places its child at the top-leading origin —
        // and hands us the CANVAS size, which IS these boxes' containing
        // block (css-position-3 §3.1: the viewport for fixed; the
        // initial containing block for un-ancestored absolute roots).
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
    }
}
