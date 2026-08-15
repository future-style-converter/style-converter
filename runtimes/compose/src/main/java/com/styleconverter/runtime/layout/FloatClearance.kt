package com.styleconverter.runtime.layout

// FloatClearance — CSS 2.2 §9.5.2 clearance, the wave-42 lane-W5 pure
// twin (iOS: StyleEngine/layout/FloatClearance.swift, byte-parallel).
//
// What the natives got wrong (wave-41 T7 evidence, CSS2/floats-clear):
// single floats were laid as IN-FLOW stacked blocks, and a clearing
// sibling's margin-top applied in full — so adjoining-float-before-
// clearance rendered green/400px-red/green where Chromium's ref is one
// 100px green square. §9.5.2's actual contract:
//   • a float is out of flow: it takes NO block-axis space in its parent;
//   • an in-flow box with `clear` whose HYPOTHETICAL top border edge
//     (normal §8.3.1 collapsing, clear:none) is not past the relevant
//     float bottoms gets CLEARANCE: spacing above its margin-top sized so
//     its top border edge lands EXACTLY at the float's bottom outer edge
//     (the greater-of rule collapses to that when clearance triggers);
//   • clearance inhibits the cleared box's top margin from collapsing
//     with preceding/parent margins — the whole adjoining group (its own
//     margin-top plus the first-in-flow-descendant chain, e.g. the
//     -1000px child of clear-on-parent-with-margins) is ABSORBED;
//   • when the cleared box's margin group would collapse THROUGH ancestor
//     top edges (§8.3.1 adjoining chains), any float anchored inside a
//     crossed ancestor would be PULLED DOWN with the margin — so
//     clearance is forced no matter how large the margin is
//     (adjoining-float-before-clearance's assert text verbatim).
//
// The resolver simulates that model over the projected box tree
// (FloatClearanceModel) and emits per-component adjustments the two
// native renderers apply mechanically: zero flow height for floats, and
// an applied-margin override for the cleared box + its absorbed chain.
// A null resolve is the IDENTITY (frozen pre-wave-42 rendering).

import com.styleconverter.runtime.core.ir.IRComponent

/** Per-component §9.5.2 adjustment (consumed by both block child loops). */
data class ClearanceAdjustment(
    // True for floats: the box paints at its flow slot but reports ZERO
    // block-axis size to the stack (out-of-flow, §9.5/§9.5.2).
    val zeroFlowHeight: Boolean = false,
    // Non-null for the cleared box and its absorbed chain: the APPLIED
    // top margin replacing the declared one (clearance + absorbed group
    // folded in; 0 for absorbed descendants).
    val appliedTopPx: Double? = null,
    // The applied bottom margin riding the same override channel (the
    // declared value passes through unchanged — §9.5.2 only touches top).
    val appliedBottomPx: Double? = null,
)

/** A whole scope's resolution, id-keyed (order-independent on purpose). */
data class FloatClearancePlan(
    // Every component id inside the scope (root included): gates the
    // §8.3.1 collapse-plan suppression so the two emulations never both
    // move the same margins.
    val scopeIds: Set<String>,
    // The per-component adjustments; ids absent here render untouched.
    val adjustments: Map<String, ClearanceAdjustment>,
)

object FloatClearance {

    /**
     * Resolve a §9.5.2 plan for [root]'s subtree, or null (identity).
     * [root] must be a composed ROOT component (slot-parentless) or a BFC
     * root: the scope's floats and clear box then provably share [root]'s
     * block formatting context, so no out-of-scope float can matter.
     * Pure — the JVM/XCTest pin tables run it device-free.
     */
    fun resolve(root: IRComponent): FloatClearancePlan? {
        // Cheap pre-gate: without BOTH a Float and a Clear wire somewhere
        // in the subtree no scope can exist — skip the full projection
        // (this runs for every block container in WPT capture).
        if (!hasWire(root, "Float") || !hasWire(root, "Clear")) return null
        // Attach gate (see kdoc): without it a mid-tree resolve could miss
        // same-BFC floats above it and compute a short clear line.
        val tree = FloatClearanceModel.project(root, null) ?: return null
        if (root.slot?.parent != null && !tree.bfcRoot) return null
        // Depth-first box list in document order — the order both natives
        // render block children (Order bails in the projection).
        val ordered = mutableListOf<ClearanceNode>()
        fun walk(n: ClearanceNode) { ordered.add(n); n.children.forEach(::walk) }
        walk(tree)
        // The scope's floats and clearing boxes.
        val floats = ordered.filter { it.floatSide != FloatValue.NONE }
        val clears = ordered.filter { it.clear != ClearValue.NONE }
        // Nothing to do without both actors; MULTIPLE clear boxes would
        // need sequential clearance state (the `<br clear>` ladders of
        // css-writing-modes abs-pos-border-offset-*) — out of scope.
        if (floats.isEmpty() || clears.size != 1) return null
        val cleared = clears.single()
        // Every float must PRECEDE the cleared box in document order (and
        // so cannot be its descendant): §9.5.2 clears PAST floats, and a
        // float inside/after the cleared box (adjoining-float-nested-
        // forced-clearance's inner 10px float) is different geometry.
        val clearedAt = ordered.indexOf(cleared)
        if (floats.any { ordered.indexOf(it) > clearedAt }) return null
        // ≥2 consecutive same-side float siblings are the wave-19 RUN
        // shape — FloatRowPacking owns those; both models firing on one
        // container would place the floats twice.
        for (n in ordered) {
            for (i in 1 until n.children.size) {
                val a = n.children[i - 1]; val b = n.children[i]
                if (a.floatSide != FloatValue.NONE && a.floatSide == b.floatSide) return null
            }
        }
        // Floats need provable extents for the ledger: explicit px height
        // (margins already px-proven by the projection).
        if (floats.any { it.heightPx == null }) return null
        // …and those extents must be the WHOLE block-axis outer size. Under
        // the CSS initial `box-sizing: content-box` (css-sizing-3 §5.1) the
        // float's margin edge is marginTop + paddingTop + height +
        // paddingBottom + marginBottom, but floatBottomOuter below adds only
        // the margins and the height — a padded float would clear to a line
        // short by its padding band. A BoxSizing wire flips WHICH of the two
        // readings is right (border-box folds the padding into `height`), and
        // the projection cannot prove the used value either way. So bail the
        // whole scope, exactly like the projection's Border bail: the file
        // banner's contract is that the plan fires only when every simulated
        // position is exact, never as a guess.
        if (floats.any {
                it.paddingTopPx != 0.0 || it.paddingBottomPx != 0.0 ||
                    it.comp.properties.any { p -> p.type == "BoxSizing" }
            }
        ) return null
        // At most ONE float per side: two same-side floats stack per
        // §9.5.1 rules 2/3 (beside or below each other), geometry this
        // ledger does not model — opposite sides coexist at the top
        // (clear-on-child-with-margins-2's left/right pair).
        if (floats.count { it.floatSide == FloatValue.LEFT } > 1) return null
        if (floats.count { it.floatSide == FloatValue.RIGHT } > 1) return null
        // FLOW DISCIPLINE — every box on a float's or the cleared box's
        // ancestor chain must be the first real in-flow content of its
        // parent (preceding siblings float or zero-outer): that pins every
        // anchor at its parent's content top, the only flow arithmetic
        // this lane implements (see zeroOuter below).
        val anchored = floats + cleared
        for (box in anchored) {
            var cur: ClearanceNode = box
            while (true) {
                val p = cur.parent ?: break
                // An INTERMEDIATE wrapper (strictly between an anchored box
                // and the scope root) carrying a block-start margin is NOT
                // modelable by the literal contentTop ledger below: by the
                // first-in-flow rule this loop enforces, that margin is
                // adjoining to its parent's top margin (§8.3.1) and can
                // escape the scope root entirely, so adding it literally
                // would misplace the box. Bail instead. The anchored boxes'
                // OWN margins stay modeled (a float's in floatBottomOuter,
                // the cleared box's in the §8.3.1 group below).
                if (cur !== box && cur.marginTopPx != 0.0) return null
                val idx = p.children.indexOf(cur)
                for (i in 0 until idx) {
                    val sib = p.children[i]
                    if (sib.floatSide == FloatValue.NONE && !zeroOuter(sib)) return null
                }
                cur = p
            }
        }
        // Absolute content-top positions (scope-root coordinates): with
        // the flow discipline above, a box's border top is its parent's
        // content top plus its own (literal) margin-top, and content top
        // adds the block-start padding (borders bailed in the projection).
        fun contentTop(n: ClearanceNode): Double {
            val p = n.parent ?: return n.paddingTopPx // root border top = 0
            return contentTop(p) + n.marginTopPx + n.paddingTopPx
        }
        // Float ledger: top = parent's content top + own margin-top;
        // bottom OUTER edge adds height + margin-bottom (§9.5.2 clears
        // past the bottom *outer* edge, i.e. the margin edge).
        fun floatBottomOuter(f: ClearanceNode): Double =
            contentTop(f.parent!!) + f.marginTopPx + f.heightPx!! + f.marginBottomPx
        // The cleared box's §8.3.1 top-margin GROUP: its own margin-top
        // plus the first-in-flow-descendant chain while nothing separates
        // the margins (no top padding, no BFC root, single-child shape —
        // a zero-outer first child with later siblings would need the
        // transitive self-collapsing fold this lane doesn't implement).
        val group = mutableListOf(cleared.marginTopPx)
        val absorbed = mutableListOf<ClearanceNode>()
        var cur = cleared
        while (!cur.bfcRoot && cur.paddingTopPx == 0.0 && cur.children.isNotEmpty()) {
            val c = cur.children.first()
            if (zeroOuter(c) && cur.children.size > 1) return null
            group.add(c.marginTopPx); absorbed.add(c); cur = c
        }
        // §8.3.1 collapsed value: max of positives plus min of negatives.
        val collapsedGroup = (group.filter { it > 0 }.maxOrNull() ?: 0.0) +
            (group.filter { it < 0 }.minOrNull() ?: 0.0)
        // ASCENT — which ancestor top edges would the group collapse
        // through with clear:none? Crossing is blocked by top padding, by
        // a BFC root, or by preceding real in-flow content; a crossed
        // ancestor is DISPLACED by the margin in the hypothetical, pulling
        // every float inside it down with it (the forced-clearance case).
        val crossed = mutableSetOf<ClearanceNode>()
        var climb: ClearanceNode = cleared
        while (true) {
            val p = climb.parent ?: break
            // First-in-flow check for the climbing box (floats/zero-outer
            // siblings are margin-transparent per §8.3.1's adjoining rule).
            val idx = p.children.indexOf(climb)
            if ((0 until idx).any { p.children[it].floatSide == FloatValue.NONE && !zeroOuter(p.children[it]) }) break
            // Blockers: padding band or BFC edge stops the chain (§8.3.1).
            if (p.paddingTopPx != 0.0 || p.bfcRoot) break
            // A crossed ancestor with its own margin would join the group;
            // that fold is not modeled — bail rather than misplace.
            if (p.marginTopPx != 0.0) return null
            crossed.add(p); climb = p
        }
        // Relevant floats for this clear side (BOTH clears either side).
        val relevant = floats.filter {
            cleared.clear == ClearValue.BOTH ||
                (cleared.clear == ClearValue.LEFT || cleared.clear == ClearValue.INLINE_START) == (it.floatSide == FloatValue.LEFT)
        }
        if (relevant.isEmpty()) return null
        // The clear line: lowest relevant bottom outer edge (§9.5.2).
        val clearLine = relevant.maxOf { floatBottomOuter(it) }
        // Hypothetical top border edge (clear:none, §8.3.1 collapsing):
        // the group anchors at the cleared box's flow slot.
        val anchor = contentTop(cleared.parent ?: return null)
        val hypothetical = anchor + collapsedGroup
        // Forced case: a relevant float inside a crossed (displaced)
        // ancestor moves WITH the margin — it can never be passed.
        val pulled = relevant.any { f ->
            generateSequence(f.parent) { it.parent }.any { it in crossed }
        }
        // No clearance → identity (clear-on-parent-with-margins-no-
        // clearance keeps its frozen rendering byte-for-byte).
        if (!pulled && hypothetical >= clearLine) return null
        // Applied top margin: places the top border edge exactly on the
        // clear line (greater-of rule under a triggered clearance). A
        // negative applied margin would need offset plumbing — bail.
        val appliedTop = clearLine - anchor
        if (appliedTop < 0.0) return null
        // Absorbed descendants must carry no unmodeled bottom margins
        // (their tops are absorbed; §8.3.1 bottom folds are out of scope).
        if (absorbed.any { it.marginBottomPx != 0.0 }) return null
        // The cleared box's bottom margin passes through unchanged, but
        // rides the same override channel — negative would leave scope.
        if (cleared.marginBottomPx < 0.0) return null
        // Emit: every float zero-flows (out of flow), the cleared box gets
        // the clearance-adjusted margin, absorbed chain members get 0.
        val adjustments = mutableMapOf<String, ClearanceAdjustment>()
        floats.forEach { adjustments[it.comp.id] = ClearanceAdjustment(zeroFlowHeight = true) }
        adjustments[cleared.comp.id] =
            ClearanceAdjustment(appliedTopPx = appliedTop, appliedBottomPx = cleared.marginBottomPx)
        absorbed.forEach {
            adjustments[it.comp.id] = ClearanceAdjustment(appliedTopPx = 0.0, appliedBottomPx = 0.0)
        }
        return FloatClearancePlan(ordered.map { it.comp.id }.toSet(), adjustments)
    }

    /** Any box in [comp]'s subtree declaring the [type] wire at all. */
    private fun hasWire(comp: IRComponent, type: String): Boolean =
        comp.properties.any { it.type == type } ||
            comp.children.orEmpty().any { hasWire(it, type) }

    /**
     * A margin-transparent zero box (§8.3.1 self-collapsing shape): no
     * explicit height, no margins/paddings, not a BFC root, and every
     * child a float or itself zero-outer — contributes exactly 0 to flow
     * and lets adjoining margins pass through (the `<div>` wrapper around
     * adjoining-float-before-clearance's float).
     */
    private fun zeroOuter(n: ClearanceNode): Boolean =
        n.floatSide == FloatValue.NONE && n.clear == ClearValue.NONE &&
            n.heightPx == null && n.marginTopPx == 0.0 && n.marginBottomPx == 0.0 &&
            n.paddingTopPx == 0.0 && n.paddingBottomPx == 0.0 && !n.bfcRoot &&
            n.children.all { it.floatSide != FloatValue.NONE || zeroOuter(it) }
}
