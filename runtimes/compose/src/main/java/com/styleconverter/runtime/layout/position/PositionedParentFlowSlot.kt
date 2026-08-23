package com.styleconverter.runtime.layout.position

// Wave 46 (lane Y6) — the STATIC-POSITION flow slot of an out-of-flow child
// whose DIRECT parent is its containing block through `position: absolute |
// fixed | sticky`.
//
// ## The measured defect (wave45-final, css-color/composited-filters-under-
// opacity.html, android-ref 0.9286 FAIL; web 1.0000 / iOS 0.9816 PASS)
// The IR is correct (X7 audit): a `position: absolute; opacity: .5` parent
// holding TWO `position: absolute` children, `left: 0` and `left: 50px`, no
// `top`. Chromium paints them OVERLAPPING at the parent's top (the ref is one
// uniform light-blue 150×150 square); Android painted them STACKED — the
// second square 150px lower, down-and-right of the first.
//
// Why: ComponentRenderer's positioned-container branch (the `Box` overlay
// that mounts abspos children at the containing block's origin) is gated on
// `position: relative` parents (and transform-CB parents since wave 35). An
// `absolute`/`fixed`/`sticky` parent — every bit as much a containing block
// under CSS 2.1 §10.1 — renders its children through the plain block
// `Column` loop instead, where an abspos child keeps its FULL flow footprint:
// the first child advances the Column's cursor by its 150px height and the
// second child's static position lands below it. CSS 2.1 §9.3.2 /
// css-position-3 §2.1 say the opposite: an out-of-flow box takes NO space in
// its parent's flow, so two consecutive abspos siblings share ONE static
// position (§10.6.4: "where it would have been in flow" — and with the first
// sibling out of flow, that is the same line for both).
//
// The same mechanism mis-placed every multi-abspos family under an abspos
// parent in the frozen corpus: filter-effects/backdrop-filter-clip-rect
// (children `top: 50` / `top: -34` rendered at 50 / 116), -edge-clipping
// (four 30×30 boxes at tops −31/101/35/35 rendered at −31/131/95/125),
// -plus-filter, css-transforms/css-transform-scale-002 (the scaled square
// at y=98 instead of 0) — each one a `cursor + top` placement where the
// spec says `containing-block origin + top`.
//
// ## The fix — the wave-18 RC1 mount, one level deeper
// RC1 (CanvasRootHoist.rendersInFlowAsStaticPosition) already renders an
// inset-free abspos box with NO positioned ancestor "in its flow slot but as
// an out-of-flow box": measured unbounded (or §10.3.7 shrink-to-fit when
// width is auto), reporting 0×0 so no sibling moves, placed at the slot
// origin. This file applies the SAME anchor to the positioned-parent case
// the Column loop owns: the slot origin is the parent's content-box corner
// at the current flow cursor, which — because every out-of-flow sibling
// before it reported zero — is the static position §10.6.4 prescribes; the
// child's own PositionApplier offset then adds its declared insets from
// that corner exactly as the Box branch's children do.
//
// ## What it deliberately does NOT do (documented, not silent)
//  * A `top`/`bottom` inset on a child that FOLLOWS in-flow siblings still
//    measures from the flow cursor, not from the containing block's top.
//    That is the pre-wave-46 behaviour of this loop, unchanged: fixing it
//    needs the Box-branch overlay (a containing-block origin the Column
//    does not expose per child), which is the positioned-container branch's
//    job, not this slot's.
//  * Both axes CAN end-anchor here, and the block axis is the one with a
//    caveat. Compose's Column hands each NON-WEIGHTED child a BOUNDED
//    mainAxisMax — the remaining main-axis space, i.e. its own maxHeight
//    minus what earlier children took — whenever its own maxHeight is
//    finite, which is every definite-height parent (an abspos parent with
//    a declared `height`, the corpus shape). So a `bottom`-only child DOES
//    end-anchor under such a parent, and because every preceding
//    out-of-flow sibling reports 0×0 (this very slot), the remaining space
//    it anchors to IS the containing block's content height — spec-correct.
//    Two honest limits: under an AUTO-height parent the bound is whatever
//    the GRANDPARENT handed down, so the anchor lands on the grandparent's
//    remaining extent rather than this parent's box; and after IN-FLOW
//    siblings the remaining space is short by their height — the same
//    flow-cursor limitation the bullet above names for `top`. A truly
//    unbounded slot (an out-of-flow parent measured with `Constraints()`,
//    an intrinsic pass) still degrades to EndInsetAnchor's A4 — start
//    anchor, the child's own −bottom offset, exactly as before. A
//    `right`-only child end-anchors the same way: the Column's bounded
//    width is the containing block's content width.
//  * The parent's padding box vs content box: insets resolve from the
//    content-box corner (the Column's content origin), the same
//    approximation the relative-parent Box branch already carries.
//  * An abspos `<li>` under a list parent renders through the Column loop's
//    RenderListItemMarker arm (marker + item), which does not take the
//    itemModifier channel — it keeps its flow footprint. No corpus test
//    carries that shape (wave-46 Y6 scan of all 29 frozen sections).
//  * A child of a STATIC parent whose containing block is a positioned
//    GRANDPARENT (css-grid/abspos/descendant-static-position-00x) is out of
//    scope: it keeps the Column footprint it had, because zeroing it would
//    also shrink the static parent's auto height — an unmeasured change on
//    four currently-passing Android cells.
//
// Host-gated like RC1: only the composed WPT capture (CanvasRootHoist.Host)
// activates it, so the dark-stage per-component canvas and every committed
// baseline keep the byte-identical legacy Column footprint.
//
// Pure decisions live here (JVM-pinned by PositionedParentFlowSlotTest —
// no Robolectric in this suite); the measurement itself is
// CanvasRootHoist.zeroFlowAnchor, the ONE out-of-flow anchor this runtime
// has, extended with a slot-relative end anchor for this call site.

import androidx.compose.ui.Modifier
import com.styleconverter.runtime.core.ir.IRProperty

object PositionedParentFlowSlot {

    /**
     * The parent position keywords that make the parent the containing
     * block of an absolute child (CSS 2.1 §10.1: any value but `static`)
     * AND route its children through ComponentRenderer's block `Column`
     * loop. `relative` is absent on purpose: a relative parent (and a
     * transform-CB parent) takes the positioned-container `Box` branch,
     * whose RenderAbsoluteChild mount already owns the zero footprint.
     */
    private val COLUMN_LOOP_CONTAINING_BLOCKS = setOf(
        PositionType.ABSOLUTE, PositionType.FIXED, PositionType.STICKY,
    )

    /** One wire decoder for both sides — the hoist's own PositionExtractor. */
    private fun positionTypeOf(properties: List<IRProperty>): PositionType =
        PositionExtractor.extractPositionConfig(properties.map { it.type to it.data }).type

    /**
     * Does [childProperties] describe an out-of-flow box (`absolute` or
     * `fixed` — css-position-3 §2.1; `relative`/`sticky` stay in flow)
     * whose direct parent [parentProperties] is a Column-loop containing
     * block (see [COLUMN_LOOP_CONTAINING_BLOCKS])?
     *
     * Pure truth table over the IR, pinned in PositionedParentFlowSlotTest.
     * The host gate is the CALLER's (it is a CompositionLocal read), so the
     * predicate itself never depends on composition state.
     */
    fun applies(
        childProperties: List<IRProperty>,
        parentProperties: List<IRProperty>,
    ): Boolean {
        val child = positionTypeOf(childProperties)
        val childOutOfFlow = child == PositionType.ABSOLUTE || child == PositionType.FIXED
        return childOutOfFlow && positionTypeOf(parentProperties) in COLUMN_LOOP_CONTAINING_BLOCKS
    }

    /**
     * The mount: [CanvasRootHoist.zeroFlowAnchor] with
     *  - the RC1 shrink-to-fit spec, so a `width: auto` child wraps at the
     *    parent's content width (§10.3.7's available width for a box whose
     *    containing block IS the parent) while a declared width keeps the
     *    unbounded measure and may overflow (css-position-3 §2.1);
     *  - the slot-relative END anchor for a `right`-only axis, read through
     *    PositionedAncestorAnchor.anchorGate — the SAME raw-wire gate the
     *    Box branch uses, so a `calc()`/`var()` start inset withdraws the
     *    anchor here exactly as it does there (anchor and offset must never
     *    compose from opposite edges).
     * Rides RenderComponent's `itemModifier` channel (outermost), exactly
     * like the RC1 mount and the Box branch's RenderAbsoluteChild mount.
     */
    fun mount(childProperties: List<IRProperty>): Modifier =
        CanvasRootHoist.zeroFlowAnchor(
            shrinkToFit = CanvasRootHoist.staticPositionShrinkToFit(childProperties),
            slotEndAnchor = PositionedAncestorAnchor.anchorGate(
                childProperties.map { it.type to it.data },
            ),
        )

    /**
     * The end edge, in device px, a slot-relative axis anchors to — the
     * slot's own bounded max constraint (the containing block's extent on
     * that axis) when the box anchors from the end there, else null to keep
     * the start anchor (EndInsetAnchor A4). Delegates to
     * [PositionedAncestorAnchor.axisFillPx] so the nested Box mount and
     * this Column mount share one rule; the Float return matches the
     * `endEdgePx` type [CanvasRootHoist.anchorPlacePx] consumes. Pure.
     */
    internal fun slotEndEdgePx(anchorsFromEnd: Boolean, boundedMaxPx: Int?): Float? =
        PositionedAncestorAnchor.axisFillPx(anchorsFromEnd, boundedMaxPx)?.toFloat()
}
