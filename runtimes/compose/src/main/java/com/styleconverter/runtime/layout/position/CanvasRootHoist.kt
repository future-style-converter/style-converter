package com.styleconverter.runtime.layout.position

// Wave 17 — the out-of-flow contract (css-position-3 §2.1/§3.2). One shared
// mechanism, pinned identically on both natives: FIXED boxes anchor at the
// VIEWPORT (our capture canvas at its UNPADDED origin), and ABSOLUTE boxes
// with NO positioned ancestor anchor at the initial containing block — the
// same unpadded canvas origin the web/Chromium reference measures (the WPT
// composed capture pinned the ABSOLUTE ancestor `left:100` at canvas
// (100,0), NOT (116,0), so the ICB anchor is the canvas edge, not the padded
// body content box). Both kinds leave the flow entirely: no space reserved
// in the parent (S5), and the parent's flow slot must NOT contribute to the
// painted position (the diagnosed wave-17 bug added natural-flow position +
// inset; insets are absolute anchors, not deltas — F3).

// Compose layout plumbing for the zero-size overlay anchor.
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
// IR model — the hoist decision and the descendant walk are PURE over the IR
// (JVM-pinned by CanvasRootHoistTest, no Robolectric in this repo).
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty

/**
 * Canvas-root hoist for out-of-flow boxes.
 *
 * [Host] wraps a composed document's content at the CANVAS ROOT — before the
 * canvas padding — and renders every hoist-eligible descendant (any depth;
 * see [collectCanvasHoisted]) in an overlay ABOVE the in-flow content,
 * anchored at the unpadded canvas origin. Each hoisted component's own style
 * chain (PositionApplier's absoluteOffset) then places it at (left, top)
 * from that origin — pins S1–S3. ComponentRenderer's interception (see
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
     */
    internal fun rendersInFlowAsStaticPosition(
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
     * Zero-flow anchor — the ONE measurement wrapper both out-of-flow
     * mounting modes share: measure the component UNBOUNDED
     * (Constraints() == 0..∞ — its own width/height modifiers decide, the
     * same rationale as the wave-8 absposOverflowMeasure), report 0×0 (see
     * [hoistedFlowReportPx]) so the box occupies NO flow space
     * (css-position-3 §2.1 / pin S5), and place the ink at (0,0) — the
     * slot's own origin. Compose draws beyond a reported size unclipped,
     * matching CSS overflow:visible.
     *  - As the overlay's [canvasAnchor], (0,0) is the host Box's
     *    top-left, i.e. the unpadded canvas origin; the component's OWN
     *    PositionApplier absoluteOffset(left, top) then lands it at
     *    canvas (left, top).
     *  - As the wave-18 static-position anchor (RC1 — see
     *    [rendersInFlowAsStaticPosition]), (0,0) is the box's FLOW slot
     *    origin, which IS the static position css-position-3 §3.1 assigns
     *    an all-auto-inset absolute box; there is no inset offset to add.
     * internal: ComponentRenderer rides it on the itemModifier channel for
     * the static-position branch, exactly like Host does for the overlay.
     */
    internal fun zeroFlowAnchor(): Modifier = Modifier.layout { measurable, _ ->
        // Unbounded measure — the box is sized by its own properties alone.
        val placeable = measurable.measure(Constraints())
        // Report zero on both axes: no flow/canvas growth from the ink.
        layout(hoistedFlowReportPx(), hoistedFlowReportPx()) {
            // Anchor at the slot origin; for hoisted boxes the child's
            // inset offset does the rest (F1/F2 anchor semantics — insets,
            // not deltas), for static-position boxes the slot origin IS
            // the final paint origin.
            placeable.place(0, 0)
        }
    }

    /**
     * The overlay slot's anchor — the shared [zeroFlowAnchor] applied at
     * the host Box's top-left (the unpadded canvas origin). Kept as its
     * own name so the Host wiring reads as the wave-17 contract it pins.
     */
    private fun canvasAnchor(): Modifier = zeroFlowAnchor()

    /**
     * The canvas-root host. Wrap the document content (INCLUDING its canvas
     * padding — the padding must sit inside so the overlay anchors at the
     * unpadded origin) and the hoisted overlay in one Box. The overlay is
     * composed AFTER the content, so with equal z it paints ABOVE in-flow
     * ink (Compose placement order == CSS tree order for equal z-index);
     * each hoisted component's own Modifier.zIndex still reorders within
     * this Box — the existing z-order machinery, unchanged.
     */
    @Composable
    fun Host(roots: List<IRComponent>, content: @Composable () -> Unit) {
        // Pure walk, memoized on the document identity.
        val hoisted = remember(roots) { collectCanvasHoisted(roots) }
        // Identity fast path: a document with no out-of-flow box renders
        // with NO host locals and NO wrapper Box — byte-identical to the
        // pre-wave-17 composed render (baseline discipline).
        if (hoisted.isEmpty()) {
            content()
            return
        }
        // One Box: child 0 is the in-flow document, children 1..n the
        // hoisted overlay slots at the shared unpadded origin.
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
                        .RenderComponent(node, itemModifier = canvasAnchor())
                }
            }
        }
    }
}
