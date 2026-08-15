//
//  FloatClearance.swift
//  StyleEngine/layout — wave-42 lane W5.
//
//  CSS 2.2 §9.5.2 clearance, the pure math twin of Compose
//  layout/FloatClearance.kt (byte-parallel — the shared pin tables in
//  FloatClearanceTest.kt ↔ FloatClearanceTests.swift diff verbatim).
//
//  What the natives got wrong (wave-41 T7 evidence, CSS2/floats-clear):
//  single floats were laid as IN-FLOW stacked blocks and a clearing
//  sibling's margin-top applied in full — adjoining-float-before-
//  clearance rendered green/400px-red/green where Chromium's ref is one
//  100px green square. §9.5.2's actual contract:
//    • a float is out of flow: NO block-axis space in its parent;
//    • an in-flow box with `clear` whose HYPOTHETICAL top border edge
//      (normal §8.3.1 collapsing, clear:none) is not past the relevant
//      float bottoms gets CLEARANCE: its top border edge lands EXACTLY
//      at the float's bottom outer edge (the greater-of rule collapses
//      to that when clearance triggers);
//    • clearance inhibits the cleared box's top-margin group (its own
//      margin plus the first-in-flow-descendant chain, e.g. the -1000px
//      child of clear-on-parent-with-margins) — the group is ABSORBED;
//    • when that group would collapse THROUGH ancestor top edges, any
//      float anchored inside a crossed ancestor would be PULLED DOWN
//      with the margin — clearance is forced no matter how large the
//      margin is (adjoining-float-before-clearance's assert verbatim).
//
//  A nil resolve is the IDENTITY (frozen pre-wave-42 rendering).
//

// Foundation for Double math; the SwiftUI adapter lives in
// ClearanceZeroFlow.swift so XCTest runs this file view-free.
import Foundation

enum FloatClearance {

    /// Per-component §9.5.2 adjustment (consumed by the block child loop).
    struct Adjustment: Equatable {
        /// True for floats: paint at the flow slot, report ZERO height.
        var zeroFlowHeight: Bool = false
        /// Non-nil for the cleared box / absorbed chain: the APPLIED top
        /// margin replacing the declared one (clearance folded in).
        var appliedTopPx: Double? = nil
        /// The applied bottom margin riding the same override channel.
        var appliedBottomPx: Double? = nil
    }

    /// A whole scope's resolution, id-keyed (order-independent).
    struct Plan: Equatable {
        /// Every component id in the scope (root included): gates the
        /// §8.3.1 collapse-plan suppression so the two emulations never
        /// both move the same margins.
        let scopeIds: Set<String>
        /// Per-component adjustments; absent ids render untouched.
        let adjustments: [String: Adjustment]
    }

    /// Resolve a §9.5.2 plan for `root`'s subtree, or nil (identity).
    /// `root` must be a composed ROOT (slot-parentless) or a BFC root:
    /// the scope's floats and clear box then provably share `root`'s
    /// block formatting context — no out-of-scope float can matter.
    static func resolve(_ root: IRComponent) -> Plan? {
        // Cheap pre-gate: without BOTH a Float and a Clear wire in the
        // subtree no scope can exist (this runs per block container).
        guard hasWire(root, "Float"), hasWire(root, "Clear") else { return nil }
        // Strict projection; any unproven box aborts (model file banner).
        guard let tree = FloatClearanceModel.project(root, parent: nil) else { return nil }
        // Attach gate (see above) — mid-tree non-BFC attach could miss
        // same-BFC floats above it and compute a short clear line.
        if root.slot?.parent != nil && !tree.bfcRoot { return nil }
        // Depth-first box list in document order (Order wires bailed).
        var ordered: [ClearanceNode] = []
        func walk(_ n: ClearanceNode) { ordered.append(n); n.children.forEach(walk) }
        walk(tree)
        // The scope's actors.
        let floats = ordered.filter { $0.floatSide != nil }
        let clears = ordered.filter { $0.clear != nil }
        // Nothing to do without both; MULTIPLE clear boxes would need
        // sequential clearance state (`<br clear>` ladders) — bail.
        guard !floats.isEmpty, clears.count == 1, let cleared = clears.first else { return nil }
        // Every float must PRECEDE the cleared box in document order
        // (a float inside/after it — adjoining-float-nested-forced-
        // clearance's inner 10px float — is different geometry).
        guard let clearedAt = ordered.firstIndex(where: { $0 === cleared }) else { return nil }
        for f in floats where ordered.firstIndex(where: { $0 === f })! > clearedAt { return nil }
        // ≥2 consecutive same-side float siblings are the wave-19 RUN
        // shape — FloatRowPacking owns those (double placement otherwise).
        for n in ordered {
            for i in 1..<max(1, n.children.count) where
                n.children[i - 1].floatSide != nil &&
                n.children[i - 1].floatSide == n.children[i].floatSide { return nil }
        }
        // Floats need provable extents for the ledger.
        if floats.contains(where: { $0.heightPx == nil }) { return nil }
        // …and those extents must be the WHOLE block-axis outer size. Under
        // the CSS initial `box-sizing: content-box` (css-sizing-3 §5.1) the
        // float's margin edge is marginTop + paddingTop + height +
        // paddingBottom + marginBottom, but bottomOuter below adds only the
        // margins and the height — a padded float would clear to a line short
        // by its padding band. A BoxSizing wire flips WHICH of the two
        // readings is right (border-box folds the padding into `height`), and
        // the projection cannot prove the used value either way. So bail the
        // whole scope, exactly like the projection's Border bail: the model
        // banner's contract is that the plan fires only when every simulated
        // position is exact, never as a guess. (Byte-parallel with the
        // Compose twin's identical bail.)
        if floats.contains(where: {
            $0.paddingTopPx != 0 || $0.paddingBottomPx != 0 ||
                $0.comp.properties.contains { p in p.type == "BoxSizing" }
        }) { return nil }
        // At most ONE float per side: same-side pairs stack per §9.5.1
        // rules 2/3 — geometry this ledger does not model.
        if floats.filter({ $0.floatSide == "LEFT" }).count > 1 { return nil }
        if floats.filter({ $0.floatSide == "RIGHT" }).count > 1 { return nil }
        // FLOW DISCIPLINE — every box on a float's or the cleared box's
        // ancestor chain must be the first real in-flow content of its
        // parent (preceding siblings float or zero-outer): that pins
        // every anchor at its parent's content top, the only flow
        // arithmetic this lane implements.
        for box in floats + [cleared] {
            var cur = box
            while let p = cur.parent {
                // An INTERMEDIATE wrapper (strictly between an anchored box
                // and the scope root) carrying a block-start margin is NOT
                // modelable by the literal contentTop ledger below: by the
                // first-in-flow rule this loop enforces, that margin is
                // adjoining to its parent's top margin (§8.3.1) and can
                // escape the scope root entirely, so adding it literally
                // would misplace the box. Bail instead. The anchored boxes'
                // OWN margins stay modeled (a float's in bottomOuter, the
                // cleared box's in the §8.3.1 group below).
                if cur !== box && cur.marginTopPx != 0 { return nil }
                guard let idx = p.children.firstIndex(where: { $0 === cur }) else { return nil }
                for i in 0..<idx where p.children[i].floatSide == nil && !zeroOuter(p.children[i]) {
                    return nil
                }
                cur = p
            }
        }
        // Absolute content-top positions (scope-root coordinates): with
        // the flow discipline, a box's border top is its parent's
        // content top plus its own literal margin-top; content top adds
        // the block-start padding (borders bailed in the projection).
        func contentTop(_ n: ClearanceNode) -> Double {
            guard let p = n.parent else { return n.paddingTopPx } // root border top = 0
            return contentTop(p) + n.marginTopPx + n.paddingTopPx
        }
        // Float ledger: bottom OUTER edge = content top + own margin-top
        // + height + margin-bottom (§9.5.2 clears past the MARGIN edge).
        func bottomOuter(_ f: ClearanceNode) -> Double {
            contentTop(f.parent!) + f.marginTopPx + f.heightPx! + f.marginBottomPx
        }
        // The cleared box's §8.3.1 top-margin GROUP: its own margin plus
        // the first-in-flow-descendant chain while nothing separates the
        // margins (no top padding, no BFC root; a zero-outer first child
        // with later siblings would need the transitive self-collapsing
        // fold this lane doesn't implement — bail).
        var group: [Double] = [cleared.marginTopPx]
        var absorbed: [ClearanceNode] = []
        var cur = cleared
        while !cur.bfcRoot, cur.paddingTopPx == 0, let c = cur.children.first {
            if zeroOuter(c) && cur.children.count > 1 { return nil }
            group.append(c.marginTopPx); absorbed.append(c); cur = c
        }
        // §8.3.1 collapsed value: max positive plus min negative.
        let collapsedGroup = (group.filter { $0 > 0 }.max() ?? 0) +
            (group.filter { $0 < 0 }.min() ?? 0)
        // ASCENT — which ancestor top edges would the group collapse
        // through with clear:none? Blocked by top padding, a BFC root,
        // or preceding real in-flow content; a crossed ancestor is
        // DISPLACED in the hypothetical, pulling its floats with it.
        var crossed: [ClearanceNode] = []
        var climb = cleared
        while let p = climb.parent {
            guard let idx = p.children.firstIndex(where: { $0 === climb }) else { return nil }
            // First-in-flow modulo floats/zero-outer (§8.3.1 adjoining).
            if (0..<idx).contains(where: { p.children[$0].floatSide == nil && !zeroOuter(p.children[$0]) }) { break }
            // Blockers: padding band or BFC edge stops the chain.
            if p.paddingTopPx != 0 || p.bfcRoot { break }
            // A crossed ancestor's own margin would join the group; that
            // fold is not modeled — bail rather than misplace.
            if p.marginTopPx != 0 { return nil }
            crossed.append(p); climb = p
        }
        // Relevant floats for this clear side (BOTH clears either side).
        let clearsLeft = cleared.clear == "LEFT" || cleared.clear == "INLINE_START"
        let relevant = floats.filter {
            cleared.clear == "BOTH" || clearsLeft == ($0.floatSide == "LEFT")
        }
        guard !relevant.isEmpty else { return nil }
        // The clear line: lowest relevant bottom outer edge (§9.5.2).
        let clearLine = relevant.map(bottomOuter).max()!
        // Hypothetical top border edge (clear:none, §8.3.1 collapsing).
        guard let clearedParent = cleared.parent else { return nil }
        let anchor = contentTop(clearedParent)
        let hypothetical = anchor + collapsedGroup
        // Forced case: a relevant float inside a crossed (displaced)
        // ancestor moves WITH the margin — it can never be passed.
        let pulled = relevant.contains { f in
            // Explicit ancestor walk (a lazy `sequence` of optionals
            // never terminates on nil — the double-optional pitfall).
            var anc = f.parent
            while let a = anc {
                if crossed.contains(where: { $0 === a }) { return true }
                anc = a.parent
            }
            return false
        }
        // No clearance → identity (clear-on-parent-with-margins-no-
        // clearance keeps its frozen rendering byte-for-byte).
        if !pulled && hypothetical >= clearLine { return nil }
        // Applied top margin: places the top border edge exactly on the
        // clear line. A negative applied margin would need offset
        // plumbing this lane does not add — bail.
        let appliedTop = clearLine - anchor
        if appliedTop < 0 { return nil }
        // Absorbed members must carry no unmodeled bottom margins, and
        // the cleared box's pass-through bottom must stay non-negative.
        if absorbed.contains(where: { $0.marginBottomPx != 0 }) { return nil }
        if cleared.marginBottomPx < 0 { return nil }
        // Emit: floats zero-flow (out of flow), the cleared box gets the
        // clearance-adjusted margin, absorbed chain members get 0.
        var adjustments: [String: Adjustment] = [:]
        for f in floats { adjustments[f.comp.id] = Adjustment(zeroFlowHeight: true) }
        adjustments[cleared.comp.id] =
            Adjustment(appliedTopPx: appliedTop, appliedBottomPx: cleared.marginBottomPx)
        for a in absorbed {
            adjustments[a.comp.id] = Adjustment(appliedTopPx: 0, appliedBottomPx: 0)
        }
        return Plan(scopeIds: Set(ordered.map { $0.comp.id }), adjustments: adjustments)
    }

    /// Any box in `comp`'s subtree declaring the `type` wire at all.
    private static func hasWire(_ comp: IRComponent, _ type: String) -> Bool {
        comp.properties.contains { $0.type == type } ||
            (comp.children ?? []).contains { hasWire($0, type) }
    }

    /// A margin-transparent zero box (§8.3.1 self-collapsing shape): no
    /// explicit height, no margins/paddings, not a BFC root, every child
    /// a float or itself zero-outer — contributes exactly 0 to flow and
    /// lets adjoining margins pass through (the `<div>` wrapper around
    /// adjoining-float-before-clearance's float).
    static func zeroOuter(_ n: ClearanceNode) -> Bool {
        n.floatSide == nil && n.clear == nil &&
            n.heightPx == nil && n.marginTopPx == 0 && n.marginBottomPx == 0 &&
            n.paddingTopPx == 0 && n.paddingBottomPx == 0 && !n.bfcRoot &&
            n.children.allSatisfy { $0.floatSide != nil || zeroOuter($0) }
    }
}
