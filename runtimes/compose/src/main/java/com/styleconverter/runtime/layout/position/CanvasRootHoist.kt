package com.styleconverter.runtime.layout.position

// Wave 17 — the out-of-flow contract (css-position-3 §2.1/§3.2). One shared
// mechanism, pinned identically on both natives: FIXED boxes anchor at the
// VIEWPORT, and ABSOLUTE boxes with NO positioned ancestor anchor at the
// INITIAL CONTAINING BLOCK. Both kinds leave the flow entirely: no space
// reserved in the parent (S5), and the parent's flow slot must NOT
// contribute to the painted position (the diagnosed wave-17 bug added
// natural-flow position + inset; insets are absolute anchors, not deltas
// — F3).
//
// WHERE that viewport/ICB corner IS on the capture surface is the [Host]'s
// `canvasFrame` parameter, and it MOVED at wave 25 round 3. Wave 17 measured
// it as the UNPADDED canvas corner (the composed capture pinned an ABSOLUTE
// root's `left:100` at canvas x=100, not 116) — correct at the time, because
// the ref pipeline framed its pages with `:where(body){padding:16px}` and CSS
// padding on a static body moves in-flow content WITHOUT moving out-of-flow
// content. Wave 25 CAL-RC1 moved that frame into IMAGE space, where the
// translation applies to every pixel alike, so the ICB corner is now the
// FRAMED content corner (16,16). See [Host]'s `canvasFrame` doc for the full
// before/after.

// Compose layout plumbing for the zero-size overlay anchor.
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
// Dp for the wave-22 canvas extents (converted to device px inside the
// anchor's Density receiver — see zeroFlowAnchor). `dp` for the wave-25
// zero-frame defaults that keep every pre-existing call site byte-identical.
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
// IR model — the hoist decision and the descendant walk are PURE over the IR
// (JVM-pinned by CanvasRootHoistTest, no Robolectric in this repo).
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty

/**
 * Canvas-root hoist for out-of-flow boxes.
 *
 * [Host] wraps a composed document's content at the CANVAS ROOT — outside the
 * canvas padding — and renders every hoist-eligible descendant (any depth;
 * see [collectCanvasHoisted]) in an overlay ABOVE the in-flow content,
 * anchored at the INITIAL CONTAINING BLOCK's corner, i.e. the canvas origin
 * translated by the host's `canvasFrame` (wave 25 round 3; zero for callers
 * that pass no frame). Each hoisted component's own style chain
 * (PositionApplier's absoluteOffset) then places it at (left, top) from that
 * origin — pins S1–S3. ComponentRenderer's interception (see
 * [interceptsInFlow]) composes NOTHING for the same components in flow, so
 * siblings take their place (pin S5).
 *
 * Activation scope — honest and narrow: only the composed WPT capture wraps
 * in [Host] today. Hostless paths (the dark-stage per-component canvas whose
 * layout/position fixture scores 1.000 with root-level positioned
 * components, and every 327-pair baseline) never see [LocalActive] true and
 * render byte-identically to the frozen baselines.
 */
object CanvasRootHoist {

    /**
     * True under a [Host]: ComponentRenderer intercepts hoist-eligible
     * components in flow. Default false — the frozen-baseline identity path.
     */
    internal val LocalActive = compositionLocalOf { false }

    /**
     * The component instance the overlay is CURRENTLY rendering — the one
     * node whose interception must be skipped so it can render itself.
     * Reference identity is enough: an IR tree never aliases a node. The
     * bypass deliberately stays set for the hoisted node's whole subtree —
     * a deeper fixed descendant is a DIFFERENT instance, so it is still
     * intercepted there and rendered from its own overlay slot (the walk
     * collects it separately), which is exactly css-position-3's answer for
     * fixed-inside-fixed: both anchor at the viewport.
     */
    internal val LocalBypass = compositionLocalOf<IRComponent?> { null }

    /**
     * True when ANY ancestor in the composition is positioned (position !=
     * static — CSS 2.1 §10.1: such an ancestor is the containing block for
     * absolute descendants). Provided by ComponentRenderer for every
     * positioned component's subtree; read by [interceptsInFlow] so ABSOLUTE
     * boxes under a positioned ancestor keep the existing wave-8/9
     * machinery (RenderAbsoluteChild + the containing-block channel — pin
     * S4) instead of hoisting.
     */
    internal val LocalHasPositionedAncestor = compositionLocalOf { false }

    /**
     * The component's resolved position keyword, via the same
     * PositionExtractor the live style chain uses — one decoder for the
     * wire keyword, so the hoist decision can never disagree with the
     * offset applier about what "fixed" means.
     */
    private fun positionTypeOf(properties: List<IRProperty>): PositionType =
        PositionExtractor.extractPositionConfig(properties.map { it.type to it.data }).type

    /**
     * CSS 2.1 §10.1: a positioned box (anything but static) is the
     * containing block for absolute descendants. Used to thread the
     * positioned-ancestor flag down both the composition (ComponentRenderer)
     * and the pure walk ([collectCanvasHoisted]) — the two MUST mirror each
     * other or a component gets dropped from flow but never overlaid.
     */
    internal fun establishesContainingBlock(properties: List<IRProperty>): Boolean =
        positionTypeOf(properties) != PositionType.STATIC

    /**
     * Wave 18 (RC1) — does the declaration list carry ANY inset that
     * anchors the box to its containing block? Read through the SAME
     * PositionExtractor the live style chain uses (one wire decoder), so
     * "has an inset" can never disagree with the offset the applier will
     * paint. Physical (Left/Top/Right/Bottom) and logical (the
     * InsetInline / InsetBlock longhands) sides all count — the resolved
     * accessors fold logical into physical per the engine's LTR horizontal-tb
     * normalization (css-logical-1 §4.1). An `auto` / absent side
     * extracts to null and does NOT count: css-position-3 §3.1 gives an
     * all-auto-inset absolute box its STATIC position, not an anchor.
     */
    internal fun hasAnyInset(properties: List<IRProperty>): Boolean =
        PositionExtractor.extractPositionConfig(properties.map { it.type to it.data })
            .let {
                // Any resolved side non-null = at least one anchoring inset.
                it.resolvedStart != null || it.resolvedEnd != null ||
                    it.resolvedTop != null || it.resolvedBottom != null
            }

    /**
     * The hoist decision (pure, from BASE declarations — a selector/media
     * bucket that flips `position` at runtime is out of the emulation's
     * scope, same conservatism as the §8.3.1 collapse plan's B6):
     *  - FIXED always hoists — its containing block is the viewport
     *    regardless of positioned ancestors (css-position-3 §3.2; pin S3:
     *    a fixed child of an in-flow relative parent paints at canvas
     *    (left, top), NOT parent + offset);
     *  - ABSOLUTE hoists only when NO positioned ancestor exists — then its
     *    containing block is the initial containing block, i.e. the canvas
     *    (css-position-3 §3.1; pin S2) — AND (wave 18, RC1) only when at
     *    least ONE inset anchors it there. With all-auto insets the spec
     *    puts the box at its STATIC POSITION (§3.1: "where it would have
     *    been in flow"), which the canvas-origin hoist got wrong by a full
     *    flow offset (css-sizing abspos-001/002: the square painted at
     *    (0,0) over the paragraph instead of below it) — such boxes stay
     *    in their flow slot behind [rendersInFlowAsStaticPosition]'s
     *    zero-report anchor instead. Mixed-axis (inset on ONE axis only)
     *    still hoists: the inset axis needs the canvas anchor, and the
     *    auto axis approximates its static position with the canvas
     *    origin (documented approximation, pinned in CanvasRootHoistTest
     *    — every wave-17 css-position hoist case carries insets, so this
     *    branch is strictly additive there).
     * Public (not internal): the harness's composed canvas calls it to zero
     * the UA-margin gap injection for out-of-flow roots (no flow space —
     * a gap Spacer for a hoisted root would be reserved space, violating S5).
     */
    fun shouldHoistToCanvasRoot(
        properties: List<IRProperty>,
        hasPositionedAncestor: Boolean,
    ): Boolean = when (positionTypeOf(properties)) {
        // Viewport-anchored regardless of ancestry (F1). A no-inset fixed
        // box keeps the wave-17 canvas-origin anchor (kept behavior).
        PositionType.FIXED -> true
        // ICB-anchored only without a positioned ancestor (F2), and only
        // when an inset actually anchors it there (RC1 — see kdoc above).
        PositionType.ABSOLUTE -> !hasPositionedAncestor && hasAnyInset(properties)
        // static / relative / sticky stay in flow (css-position-3 §2.1).
        else -> false
    }

    /**
     * Wave 18 (RC1) — the static-position branch the hoist decision above
     * carved out: an ABSOLUTE box with NO positioned ancestor and NO inset
     * on either axis renders IN its flow slot (so it paints at its static
     * position — css-position-3 §3.1) but as an out-of-flow box: measured
     * unbounded and reporting 0×0 flow size via [zeroFlowAnchor], so no
     * sibling moves for it (pin S5, reusing the exact overlay-anchor
     * machinery). ComponentRenderer applies this exactly when a Host is
     * active; hostless paths (dark stage, 327-pair baseline) never see it.
     * Pure truth table — pinned in CanvasRootHoistTest.
     * Public (wave-19 follow-up, same precedent as [shouldHoistToCanvasRoot]):
     * the harness's composed canvas reads it to mark such a root margin-
     * TRANSPARENT in the root-stack gap fold — CSS 2.1 §8.3.1 collapses the
     * neighbors' block margins THROUGH a zero-flow-footprint box — using the
     * SAME decision the renderer's zero-flow anchor rides, never re-deriving.
     */
    fun rendersInFlowAsStaticPosition(
        properties: List<IRProperty>,
        hasPositionedAncestor: Boolean,
    ): Boolean = positionTypeOf(properties) == PositionType.ABSOLUTE &&
        !hasPositionedAncestor &&
        !hasAnyInset(properties)

    /**
     * The in-flow interception decision ComponentRenderer applies at the top
     * of RenderComponent: compose NOTHING (no box, no reserved space — S5)
     * exactly when a host is active, this instance is not the overlay's
     * current bypass, and the hoist decision says the box is out of flow.
     * Pure truth table — JVM-pinned by CanvasRootHoistTest.
     */
    internal fun interceptsInFlow(
        component: IRComponent,
        hostActive: Boolean,
        hasPositionedAncestor: Boolean,
        bypass: IRComponent?,
    ): Boolean = hostActive &&
        bypass !== component &&
        shouldHoistToCanvasRoot(component.properties, hasPositionedAncestor)

    /**
     * Depth-first, document-order walk collecting every hoist-eligible
     * descendant — the pure mirror of the composition-side interception
     * (same decision function, same positioned-ancestor threading), so
     * every intercepted box has exactly one overlay slot. The walk recurses
     * INTO hoisted nodes too (their own subtrees render in the overlay, and
     * a fixed grandchild there must still hoist to the canvas — see
     * [LocalBypass]). Document order keeps overlay paint order matching the
     * source order the web reference paints for equal z-index
     * (CSS 2.1 Appendix E tree order).
     */
    internal fun collectCanvasHoisted(
        roots: List<IRComponent>,
        hasPositionedAncestor: Boolean = false,
    ): List<IRComponent> {
        // Accumulator in visit order (document order).
        val out = mutableListOf<IRComponent>()
        // Local recursion carrying the positioned-ancestor flag per level.
        fun walk(node: IRComponent, ancestorPositioned: Boolean) {
            // Collect the node itself when the shared decision says hoist.
            if (shouldHoistToCanvasRoot(node.properties, ancestorPositioned)) out += node
            // Children see a positioned ancestor if one already existed OR
            // this node is itself positioned (CSS 2.1 §10.1).
            val childFlag = ancestorPositioned || establishesContainingBlock(node.properties)
            // Recurse in document order (children may be null on leaves).
            node.children?.forEach { walk(it, childFlag) }
        }
        // Roots start from the caller's ancestry context (canvas root: false).
        roots.forEach { walk(it, hasPositionedAncestor) }
        return out
    }

    /**
     * Wave 21 (A-RC7) — does ANY box in the document leave normal flow
     * under a [Host]? Same depth-first walk and positioned-ancestor
     * threading as [collectCanvasHoisted], but counting BOTH out-of-flow
     * classes: the hoisted boxes (fixed / inset-anchored absolute) AND the
     * wave-18 RC1 static-position boxes (no-inset absolute with no
     * positioned ancestor — they stay in their slot but report 0×0 via
     * [zeroFlowAnchor]). css-position-3 §2.1 makes no distinction: both
     * kinds are absolutely positioned and out of flow; only their ANCHOR
     * differs (§3.1 static position vs inset). Short-circuits on the first
     * hit — activation needs existence, not the list.
     */
    internal fun anyOutOfFlowBox(
        roots: List<IRComponent>,
        hasPositionedAncestor: Boolean = false,
    ): Boolean {
        // Local recursion carrying the positioned-ancestor flag per level —
        // the same CSS 2.1 §10.1 threading as collectCanvasHoisted's walk.
        fun walk(node: IRComponent, ancestorPositioned: Boolean): Boolean {
            // Either out-of-flow class counts (see kdoc): hoisted overlay…
            if (shouldHoistToCanvasRoot(node.properties, ancestorPositioned)) return true
            // …or the in-slot zero-flow static-position class.
            if (rendersInFlowAsStaticPosition(node.properties, ancestorPositioned)) return true
            // Children see a positioned ancestor if one already existed OR
            // this node is itself positioned (CSS 2.1 §10.1).
            val childFlag = ancestorPositioned || establishesContainingBlock(node.properties)
            // Recurse in document order (children may be null on leaves).
            return node.children?.any { walk(it, childFlag) } ?: false
        }
        // Roots start from the caller's ancestry context (canvas root: false).
        return roots.any { walk(it, hasPositionedAncestor) }
    }

    /**
     * Will a [Host] over these roots ACTIVATE (provide [LocalActive])?
     *
     * Wave 21 (A-RC7): true whenever ANY out-of-flow box exists — hoisted
     * OR static-position ([anyOutOfFlowBox]) — not merely when a box
     * hoists. The old hoisted-only activation left a document whose ONLY
     * out-of-flow boxes are no-inset absolutes (css-images
     * conic-gradient-line-height-relative-units-001/002: two absolute
     * roots that must overlap at the canvas origin) rendering them as
     * ordinary flow blocks with phantom flow space — the RC1 zero-flow
     * anchor is host-gated in ComponentRenderer and the host never
     * engaged. iOS is the parity oracle: its composed canvas attaches
     * StaticPositionAnchor UNGATED (CaptureCanvas.swift — driven only by
     * FixedHoist.rendersInFlowAsStaticPosition), so Android's activation
     * must be at least as broad.
     *
     * The harness's composed canvas reads this so its §8.3.1 gap fold
     * treats static-position roots as margin-transparent exactly when the
     * renderer gives them a zero flow footprint — one decision function,
     * two consumers, never re-derived.
     */
    fun hostActivates(roots: List<IRComponent>): Boolean =
        anyOutOfFlowBox(roots)

    /**
     * The flow size an overlay slot reports to the canvas on EACH axis:
     * zero. An out-of-flow box never sizes its ancestors (css-position-3
     * §3), so the overlay anchor must not grow the canvas even when the
     * hoisted ink is taller than the document (a browser's fixed box
     * overflows the viewport without extending it). Pure — the S5-side
     * wiring pin (same feasible-without-Robolectric style as
     * absposReportedAxis / FragmentGeometryTest).
     */
    internal fun hoistedFlowReportPx(): Int = 0

    /**
     * Wave 25 (round 3) — where an anchor slot places a measured box on ONE
     * axis, in whole device px. The ENTIRE placement arithmetic of
     * [zeroFlowAnchor], lifted out of the `Modifier.layout` lambda so it is
     * pinnable on the JVM (this suite has no Robolectric, so anything left
     * inside the lambda is untested by construction).
     *
     * Two terms, and only two:
     *  1. [originPx] — the containing block's START corner in the slot's own
     *     coordinate space. For an overlay slot mounted at the raw canvas
     *     corner this is the canvas frame (16dp in device px), because the
     *     ICB is the FRAMED content box under the wave-25 image-space ref
     *     frame; for ComponentRenderer's static-position mount (whose slot
     *     origin already IS the flow position) it is 0.
     *  2. [EndInsetAnchor.placePx] — 0 for a start-anchored axis (the box's
     *     own PositionApplier offset supplies `left`/`top`), or
     *     `endEdgePx − boxPx` for an end-only-inset axis (A5), where the end
     *     edge is measured FROM the same origin.
     *
     * Both terms are translations of the same box, so they simply add: a
     * `left: 100` hoisted root lands at frame + 0 (+100 from its own
     * applier), and a `right: 0` root at frame + icb − box (−0 from its
     * applier). Pinned in CanvasRootHoistTest against the css-position guard
     * coordinates.
     */
    internal fun anchorPlacePx(originPx: Int, endEdgePx: Float?, boxPx: Int): Int =
        originPx + EndInsetAnchor.placePx(endEdgePx, boxPx)

    /**
     * Zero-flow anchor — the ONE measurement wrapper both out-of-flow
     * mounting modes share: measure the component UNBOUNDED
     * (Constraints() == 0..∞ — its own width/height modifiers decide, the
     * same rationale as the wave-8 absposOverflowMeasure), report 0×0 (see
     * [hoistedFlowReportPx]) so the box occupies NO flow space
     * (css-position-3 §2.1 / pin S5), and place the ink at (0,0) — the
     * slot's own origin. Compose draws beyond a reported size unclipped,
     * matching CSS overflow:visible.
     *  - As the overlay's [canvasAnchor], the origin is the host Box's
     *    top-left plus the canvas frame — the ICB corner (wave 25 round 3;
     *    the bare Box corner when no frame is passed); the component's OWN
     *    PositionApplier absoluteOffset(left, top) then lands it at
     *    ICB (left, top).
     *  - As the wave-18 static-position anchor (RC1 — see
     *    [rendersInFlowAsStaticPosition]), (0,0) is the box's FLOW slot
     *    origin, which IS the static position css-position-3 §3.1 assigns
     *    an all-auto-inset absolute box; there is no inset offset to add.
     * internal: ComponentRenderer rides it on the itemModifier channel for
     * the static-position branch, exactly like Host does for the overlay.
     */
    internal fun zeroFlowAnchor(
        // Wave 22 (B-RC3) — the containing block's END edges in this slot's
        // own coordinate space, or null to keep the slot origin. Non-null
        // ONLY for an overlay slot whose box anchors from `right`/`bottom`
        // and whose containing-block extent is known (see [canvasAnchor] +
        // [EndInsetAnchor]); the defaults keep every existing call site —
        // ComponentRenderer's RC1 static-position mount and every hoisted
        // start-anchored box — byte-identical to wave 17/18.
        endEdgeX: Dp? = null,
        endEdgeY: Dp? = null,
        // Wave 25 (round 3) — the containing block's START corner in this
        // slot's own coordinate space. For an overlay slot mounted at the
        // OUTER canvas origin this is the canvas frame (16, 16), because the
        // ICB is the framed content box, not the image (see [canvasAnchor]).
        // Defaults (0, 0) keep ComponentRenderer's RC1 static-position mount
        // — whose slot origin already IS the flow position — byte-identical.
        originX: Dp = 0.dp,
        originY: Dp = 0.dp,
    ): Modifier = Modifier.layout { measurable, _ ->
        // Unbounded measure — the box is sized by its own properties alone.
        val placeable = measurable.measure(Constraints())
        // Report zero on both axes: no flow/canvas growth from the ink.
        layout(hoistedFlowReportPx(), hoistedFlowReportPx()) {
            // Anchor at the containing block's START corner (start-anchored
            // / static-position boxes: the child's own inset offset does the
            // rest — F1/F2 anchor semantics, insets not deltas), or flush
            // with its END edge when the box declares only `right`/`bottom`
            // (A5). The end edge is measured FROM the same origin, so both
            // branches share the one translation. Dp→px here, inside the
            // Density receiver, so the arithmetic is in the same device-px
            // space as placeable.width/height at any screen density.
            placeable.place(
                x = anchorPlacePx(originX.roundToPx(), endEdgeX?.toPx(), placeable.width),
                y = anchorPlacePx(originY.roundToPx(), endEdgeY?.toPx(), placeable.height),
            )
        }
    }

    /**
     * The overlay slot's anchor — the shared [zeroFlowAnchor] applied at
     * the INITIAL CONTAINING BLOCK's corner: the host Box's top-left plus
     * [Host]'s `canvasFrame` (wave 25 round 3 — the ref's 16px frame is
     * image-space now, so out-of-flow ink translates with the in-flow ink;
     * a zero frame reproduces the wave-17 unpadded-corner anchor exactly).
     * Kept as its own name so the Host wiring reads as the contract it pins.
     *
     * Wave 22 (B-RC3): a hoisted box whose only inset on an axis is
     * `right`/`bottom` anchors at the CANVAS's end edge instead, because
     * for these boxes the canvas IS the containing block (fixed → the
     * viewport, css-position-3 §3.2; ICB-anchored absolute → the initial
     * containing block, §3.1 — both are the 390×600 capture canvas the
     * browser-ref is captured at). Without the canvas extents (a caller
     * that does not pass them) the start anchor is kept — A4's documented
     * degradation, identical to wave 17/18 behavior.
     */
    private fun canvasAnchor(
        properties: List<IRProperty>,
        canvasWidth: Dp?,
        canvasHeight: Dp?,
        canvasFrame: Dp,
    ): Modifier {
        // Read the insets through the SAME extractor the live style chain
        // uses, so the anchor and the offset can never disagree about
        // which sides are declared.
        val config = PositionExtractor.extractPositionConfig(properties.map { it.type to it.data })
        return zeroFlowAnchor(
            // A1/A2/A3: only an end-only axis gets an end edge; A4: a null
            // extent keeps the start anchor. One rule table, one owner.
            endEdgeX = EndInsetAnchor.endEdge(config.anchorsFromEndX, canvasWidth),
            endEdgeY = EndInsetAnchor.endEdge(config.anchorsFromEndY, canvasHeight),
            // Wave 25 (round 3): the ICB's start corner inside the framed
            // canvas — see [Host]'s canvasFrame parameter. Zero for every
            // caller that passes no frame, i.e. the wave-17/18 behavior.
            originX = canvasFrame,
            originY = canvasFrame,
        )
    }

    /**
     * The canvas-root host. Wrap the document content (INCLUDING its canvas
     * padding — the padding must sit inside so the overlay's own origin is
     * the raw canvas corner, from which `canvasFrame` translates it to the
     * ICB) and the hoisted overlay in one Box. The overlay is
     * composed AFTER the content, so with equal z it paints ABOVE in-flow
     * ink (Compose placement order == CSS tree order for equal z-index);
     * each hoisted component's own Modifier.zIndex still reorders within
     * this Box — the existing z-order machinery, unchanged.
     */
    @Composable
    fun Host(
        roots: List<IRComponent>,
        // Wave 22 (B-RC3) — the capture canvas's extents, i.e. the
        // containing block every hoisted box anchors in (fixed → viewport,
        // css-position-3 §3.2; ICB-anchored absolute → the initial
        // containing block, §3.1 — both are the canvas). Needed ONLY to
        // resolve `right`/`bottom`-only insets from the END edge; nulls
        // keep the wave-17/18 start anchor for every box (A4). Defaulted so
        // any caller that has no canvas geometry compiles unchanged.
        canvasWidth: Dp? = null,
        canvasHeight: Dp? = null,
        // Wave 25 (round 3) — the CANVAS FRAME: the inset between the outer
        // capture surface (what PixelCopy grabs) and the INITIAL CONTAINING
        // BLOCK every hoisted box anchors in.
        //
        // Wave 17 pinned this at ZERO from measured evidence: the ref
        // pipeline injected its 16px canvas frame as `:where(body){padding}`,
        // and CSS padding on a static body does NOT move out-of-flow boxes
        // (their containing block is the ICB / the viewport, whose origin is
        // the canvas corner). So the ref's abspos `left:100` root really did
        // land at canvas x=100 while its in-flow prose sat at x=116 — the ref
        // was internally misaligned, and this anchor was calibrated to match
        // that misalignment.
        //
        // Wave 25 CAL-RC1 repaired the ref: the page now renders UNPADDED at
        // the content width and the frame is added to the PNG in IMAGE space
        // (capture-browser-ref.mjs padPngBuffer). A raster translation moves
        // in-flow and out-of-flow ink together, so the same `left:100` root
        // now lands at image x=116 with its prose. The hoist origin follows.
        //
        // [canvasWidth]/[canvasHeight] are the ICB extents (the 358×568
        // content space at the 390×600 defaults), measured FROM this frame —
        // so a `right: 0` box lands flush with the content edge and the
        // frame stays visible, matching the raster.
        //
        // Defaulted 0.dp: every caller that passes no frame keeps the exact
        // wave-17/18/22 placement, so the hostless paths and the frozen
        // baselines are untouched.
        canvasFrame: Dp = 0.dp,
        content: @Composable () -> Unit,
    ) {
        // Pure walks, memoized on the document identity.
        val hoisted = remember(roots) { collectCanvasHoisted(roots) }
        // Wave 21 (A-RC7): activation is BROADER than the overlay — see
        // hostActivates. hoisted non-empty implies activates (the hoist
        // class is one of the two the activation walk counts).
        val activates = remember(roots) { hostActivates(roots) }
        // Identity fast path: a document with no out-of-flow box AT ALL
        // renders with NO host locals and NO wrapper Box — byte-identical
        // to the pre-wave-17 composed render (baseline discipline).
        if (!activates) {
            content()
            return
        }
        // Wave 21 (A-RC7): out-of-flow boxes exist but NONE hoists (only
        // static-position absolutes — the conic-gradient-…-001/002 shape).
        // Provide LocalActive so ComponentRenderer's RC1 zero-flow anchor
        // engages, but add NO wrapper Box and NO overlay slots: this is a
        // composition-locals-only change, the layout tree is untouched
        // (minimal diff from the old fast path — Box's constraint loosening
        // never enters the picture for these documents).
        if (hoisted.isEmpty()) {
            CompositionLocalProvider(LocalActive provides true) { content() }
            return
        }
        // One Box: child 0 is the in-flow document, children 1..n the
        // hoisted overlay slots, each translated from this Box's corner to
        // the ICB corner by canvasFrame (wave 25 round 3).
        Box {
            // Activate interception for the whole in-flow tree.
            CompositionLocalProvider(LocalActive provides true) { content() }
            // Overlay slots in document order (tree paint order for equal z).
            hoisted.forEach { node ->
                CompositionLocalProvider(
                    // Interception stays active INSIDE the hoisted subtree
                    // (a deeper fixed box must still drop out of ITS flow).
                    LocalActive provides true,
                    // …but this node itself renders (see LocalBypass docs).
                    LocalBypass provides node,
                ) {
                    // Render through the SAME engine entry as flow content —
                    // parity is inherited, not re-implemented. The anchor
                    // modifier rides the itemModifier channel (outermost
                    // slot), exactly like absposOverflowMeasure does.
                    com.styleconverter.runtime.core.renderer.ComponentRenderer
                        .RenderComponent(
                            node,
                            // Per-node anchor (wave 22 + wave 25 round 3):
                            // start-anchored boxes sit at the ICB's start
                            // corner (the canvas frame); an end-only-inset
                            // box anchors flush with the ICB's right/bottom
                            // edge, both measured inside the frame.
                            itemModifier = canvasAnchor(
                                node.properties, canvasWidth, canvasHeight, canvasFrame,
                            ),
                        )
                }
            }
        }
    }
}
