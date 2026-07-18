//
//  MarginCollapsePlanner.swift
//  StyleEngine/spacing — Lane IOS-COLLAPSE (container-side gates).
//
//  The eligibility half of the CSS 2.1 §8.3.1 emulation (the pure fold
//  lives in MarginCollapse.swift): decide whether a container gets a
//  collapse Plan at all, and derive it from the SAME sorted in-flow child
//  array the renderer's ForEach walks (FlexboxApplier.sorted) so the
//  plan's overrides are index-aligned with the render loop by
//  construction, not by convention.
//
//  This file implements the UNIFIED COLLAPSE GATE CONTRACT's container-
//  level bails B1-B10 byte-for-byte with the Compose runtime's
//  ComponentRenderer.blockCollapsePlanFor + BlockMarginCollapse.hoistGates.
//  Divergence between the two natives is precisely the bug class this
//  contract exists to kill: the shared S1-S12 pin table in both platforms'
//  test suites asserts IDENTICAL expected values so they cannot drift.
//
//  This file owns containerPlan (the top-level bail sequence + fold call);
//  the per-child / parent-bucket eligibility PREDICATES it calls live in
//  MarginCollapseChildGates.swift, and the pure §8.3.1 fold + hoist gates
//  in MarginCollapse.swift — three files split to keep each inside the
//  house ≤300-line target.
//

// CoreGraphics for CGFloat in the edge tuples. StyleBuilder / ComponentStyle
// are same-module internals (no import needed).
import CoreGraphics

extension MarginCollapse {

    /// The container's collapse plan, or nil when the legacy sum
    /// behaviour must apply. Pure (component + built style in, plan
    /// out) so MarginCollapseTests pins every gate device-free.
    static func containerPlan(component: IRComponent,
                              style: ComponentStyle) -> Plan? {
        // GATE 1 — block containers only. §8.3.1 collapsing exists in
        // block formatting contexts; flex/grid item margins NEVER
        // collapse (css-flexbox-1 §4 / css-grid-1 §6). `.block` is the
        // renderer's VStack path; inline/flex/grid/none all bail here.
        // (CSS tables aren't a DisplayKeyword case — a `display: table`
        // declaration never reaches `.block`, so tables are covered.)
        guard style.layout.display == .block else { return nil }
        // GATE 2 — a grid-template on a block-ish aggregate routes the
        // container through CSSGridLayout (flowContainer's gridKind
        // branch), where children are grid items: no collapsing.
        if let agg = style.layout7, GridApplier.containerKind(for: agg) != nil { return nil }
        // GATE 3 — leading element text renders as an anonymous box
        // between the parent's top edge and the first child, breaking
        // §8.3.1 adjacency ("adjoining" forbids line boxes between the
        // margins). Legacy behaviour is honest there.
        if component.text?.isEmpty == false { return nil }
        // GATE 4 — a non-zero row gap means the VStack already inserts
        // spacing between children; folding collapsed margins on top
        // would double-count (gap isn't CSS-valid on block containers,
        // but the legacy renderer honours it — keep that path as-is).
        guard GapApplier.resolve(style.spacing.gap, context: style.spacing.context).row == 0
        else { return nil }
        // B6 — the plan is computed ONCE from base properties, but the
        // parent's selector (hover/focus) and media buckets re-style
        // live: a bucket that can touch padding/border/height/overflow/
        // position could flip a hoist gate AFTER the static plan baked
        // it. Bail so the legacy per-frame stacking stays authoritative.
        if parentBucketsDeclareGateAffecting(component) {
            logFallback(component, reason: "parent bucket declares gate-affecting longhand")
            return nil
        }
        // The full children list (pre-sort). Empty → nothing to collapse.
        let allChildren = component.children ?? []
        guard !allChildren.isEmpty else { return nil }
        // B4 — any absolutely/fixed-positioned child bails the WHOLE
        // container. A browser would skip the out-of-flow box and still
        // collapse the remaining siblings, but filtering it here would
        // desync the plan's index map from the render loop — BOTH natives
        // bail instead (contract-pinned conservatism, css-position-3 §2.1).
        for child in allChildren where ComponentRenderer.isOutOfFlow(child) {
            logFallback(component, reason: "out-of-flow child")
            return nil
        }
        // The EXACT render-order child array: all children are now in-flow
        // (B4 above), CSS-order sorted — mirrors contentOrPlaceholder's
        // `FlexboxApplier.sorted(inFlowChildren)` so the per-index
        // overrides line up with the rendered children.
        let children = FlexboxApplier.sorted(allChildren)
        // Classify every child; ONE ineligible child bails the whole
        // container (partial plans would mis-place every later sibling).
        var edges: [(top: CGFloat, bottom: CGFloat)] = []
        for child in children {
            // Raw display keyword (uppercased, hyphen→underscore) — read
            // directly so INLINE_BLOCK/-FLEX/-GRID stay distinguishable
            // (mapDisplay collapses them into inline/flex/grid).
            let display = rawDisplayKeyword(child)
            // B1 — `display: none` removes the box entirely (css-display-3
            // §2.6). Its margins must not fold into visible siblings; the
            // collapse-across the browser does is not index-expressible.
            if display == "NONE" {
                logFallback(component, reason: "display:none child")
                return nil
            }
            // B2 — inline-level boxes produce no block-level margins
            // (§8.3.1 collapses block-level margins only) and change the
            // line layout the plan can't model.
            if let d = display, INLINE_LEVEL_DISPLAY_KEYWORDS.contains(d) {
                logFallback(component, reason: "inline-level child")
                return nil
            }
            // B3 — a float's margins never collapse (§8.3.1: "margins of
            // floating boxes never collapse"). Any Float keyword != none
            // takes the box out of the collapsing flow.
            if childIsFloated(child) {
                logFallback(component, reason: "floated child")
                return nil
            }
            // B5 — selector/media buckets can swap margins in at runtime
            // state the static pre-pass can't see — bail, don't guess.
            if bucketsDeclareMargins(child) {
                logFallback(component, reason: "state/media-dependent child margins")
                return nil
            }
            // B9 — a self-collapsing candidate (no text/children, no
            // explicit block-size, BOTH vertical margins declared with
            // either positive) collapses its own top+bottom with BOTH
            // neighbours into one n-ary max the pairwise fold can't do.
            if isSelfCollapsingCandidate(child) {
                logFallback(component, reason: "self-collapsing child")
                return nil
            }
            // B8 — static non-negative px on both vertical edges, else
            // bail: negative margins (§8.3.1's deduction rule) and auto /
            // relative / calc values fall back to the legacy sum path.
            guard let e = staticVerticalEdges(child.properties) else {
                logFallback(component, reason: "negative/auto/relative child margin")
                return nil
            }
            // Eligible — record the declared edges for the fold.
            edges.append(e)
        }
        // B10 — nested-hoist chain: a FIRST or LAST child that is itself
        // an eligible unpadded/unbordered block container with children
        // would let a grandchild edge margin collapse THROUGH two levels
        // into one n-ary max the single-level emulation can't reproduce.
        // Pinned conservative on BOTH natives (§8.3.1 adjoining chains
        // are transitive). Uses the sorted first/last — the children that
        // actually touch the parent's edges in render order.
        if let first = children.first, isNestedHoistChainChild(first, edgeIsTop: true) {
            logFallback(component, reason: "nested-hoist chain")
            return nil
        }
        if let last = children.last, isNestedHoistChainChild(last, edgeIsTop: false) {
            logFallback(component, reason: "nested-hoist chain")
            return nil
        }
        // All-zero margins → the fold would be pure identity; skip the
        // plan so these containers keep the exact legacy view tree.
        guard edges.contains(where: { $0.top > 0 || $0.bottom > 0 }) else { return nil }
        // B7 — the parent's own vertical margins feed the hoist max()
        // composition (HOIST BAND MATH). They must be static non-negative
        // px from the DECLARED base properties (NOT the override-folded
        // style — reading declared margins is the double-count fix); a
        // non-static own margin bails the whole container.
        guard let parentOwn = staticVerticalEdges(component.properties) else {
            logFallback(component, reason: "negative/auto/relative parent margin")
            return nil
        }
        // Fold with the parent-edge hoist gates (§8.3.1 adjoining
        // conditions — padding/border/BFC/definite-height, gates G1-G5)
        // and the declared parent-own margins for the hoist composition.
        return fold(edges: edges,
                    parentOwn: parentOwn,
                    hoistTopAllowed: hoistAllowed(style: style, topEdge: true),
                    hoistBottomAllowed: hoistAllowed(style: style, topEdge: false))
    }
}
