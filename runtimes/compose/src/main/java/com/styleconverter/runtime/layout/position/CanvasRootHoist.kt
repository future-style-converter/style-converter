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
     * Wave 35 (lane B1) — true when ANY ancestor in the composition
     * establishes a containing block through a USED TRANSFORM rather than
     * through `position` ([TransformContainingBlock]: transform / the
     * individual transform properties / perspective / preserve-3d /
     * will-change transform). Separate from [LocalHasPositionedAncestor]
     * because the two claim DIFFERENT descendant classes: a positioned
     * ancestor is the containing block for ABSOLUTE descendants only
     * (css-position-3 §3.1), while a transformed one also claims FIXED
     * descendants (css-transforms-1 §3 / css-transforms-2 §8). Folding them
     * into one flag would have let a `position: relative` ancestor swallow a
     * fixed descendant, breaking pin S3 (a fixed child of an in-flow
     * relative parent paints at the canvas corner).
     *
     * Provided by ComponentRenderer for every transform-CB component's
     * subtree, exactly mirroring the pure walk in [collectCanvasHoisted].
     */
    internal val LocalHasTransformedAncestor = compositionLocalOf { false }

    /**
     * Wave 49 (lane A7) — the positioned-ancestor state that was in force at
     * the nearest MULTI-COLUMN CONTAINER ancestor, or null when there is no
     * multicol ancestor at all.
     *
     * Read only by
     * [com.styleconverter.runtime.columns.MulticolSpannerContainingBlock
     * .childPositionedAncestor], which restarts the §10.1 chain there for the
     * descendants of a `column-span: all` box: css-multicol-1 §6.1 lays a
     * spanner out as a block of the multi-column container, so the boxes
     * between the container and the spanner do not contain it and cannot be
     * its descendants' containing block. A THIRD channel rather than a
     * widening of [LocalHasPositionedAncestor] because the two answer
     * different questions ("is there a positioned ancestor HERE" vs "…at the
     * multicol"), and because null — "no multicol ancestor" — has to be
     * distinguishable from false so the rule stays inert outside multicol.
     *
     * Provided by ComponentRenderer for every subtree, exactly mirroring the
     * pure walks in [collectCanvasHoisted] / [anyOutOfFlowBox]; the two MUST
     * agree or a box is intercepted in flow with no overlay slot.
     */
    internal val LocalPositionedAncestorAtMulticol = compositionLocalOf<Boolean?> { null }

    /**
     * Wave 49 (lane A4) — true when ANY ancestor in the composition paints a
     * `clip-path` (css-masking-1 §5). A THIRD ancestry channel, and for a
     * different reason than the two above: this one is not about the
     * containing block at all, it is about a clip the spec gives NO escape
     * from. `clip-path` clips "the element and its descendants"; contrast
     * CSS 2.1 §11.1.1, which explicitly lets a positioned descendant escape
     * an `overflow` clip when its containing block is outside the clipping
     * box — the escape the canvas-root hoist reproduces correctly.
     *
     * Compose expresses subtree clipping only through the COMPOSITION tree
     * (`Modifier.clip` clips the node and everything its children draw), so a
     * box lifted into the canvas-root overlay becomes a SIBLING of the
     * clipping ancestor and leaves the clip behind. MEASURED on the wave-48
     * gate: WPT css-masking/clip-path/clip-path-blending-offset paints a
     * 100×100 red square at image [56,66]-[155,165] that the Chromium ref
     * (and web, and iOS) clip away entirely — android-ref 0.9566 vs 1.0000.
     *
     * Provided by ComponentRenderer for every clip-path component's subtree,
     * exactly mirroring the pure walk in [collectCanvasHoisted]. See
     * [com.styleconverter.runtime.effects.clip.establishesUnescapableClip]
     * for the predicate and the named cost of vetoing the hoist.
     */
    internal val LocalHasClippingAncestor = compositionLocalOf { false }

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
     * Wave 35 (lane B1) — css-transforms-1 §3 / css-transforms-2 §8: a box
     * with a used transform is the containing block for its positioned
     * descendants of BOTH classes. Delegates the whole clause table to
     * [TransformContainingBlock] (the twin of the Swift
     * `TransformContainingBlock`), so the hoist decision and the Swift
     * `FixedHoist.split` can never disagree about what "transformed" means.
     * Threaded down the composition as [LocalHasTransformedAncestor] and
     * down the pure walk as `ancestorTransformed`, exactly like the
     * positioned flag above.
     */
    internal fun establishesTransformContainingBlock(properties: List<IRProperty>): Boolean =
        TransformContainingBlock.establishes(properties)

    /**
     * Wave 49 (lane A4) — css-masking-1 §5: does this box paint a
     * `clip-path`, i.e. a clip its whole subtree is inside with no
     * containing-block escape? Delegates the entire decision to the clip
     * category's own predicate (which reads the SAME ClipPathExtractor the
     * live style chain runs), so the hoist veto and the emitted
     * `Modifier.clip` can never disagree about whether a clip exists.
     * Threaded down the composition as [LocalHasClippingAncestor] and down
     * the pure walk as `ancestorClipped`, exactly like the two flags above.
     *
     * TOTAL BY CONSTRUCTION (wave 49 lane F1). This is the ONE choke point
     * every clip-ancestry consumer routes through — [collectCanvasHoisted],
     * [anyOutOfFlowBox] and ComponentRenderer's `childHasClippingAncestor`
     * alike — and the two pure walks reach it for EVERY node with no early
     * return. `Host` evaluates `remember(roots) { collectCanvasHoisted(roots) }`
     * during composition and the Android harness has no error boundary, so an
     * escaping decode throw is a dead capture Activity, not a dropped
     * property. Measured pre-guard: 3 of the 1435 wave-48 corpus documents
     * threw here. See [com.styleconverter.runtime.effects.clip.clipDecodeOrElse]
     * for why `false` is the CORRECT answer and not merely the safe one.
     *
     * The guard also removes an undocumented ordering dependency: [hostActivates]
     * happened to survive those three documents only because its activation
     * test returns true BEFORE it computes the child clip flag, while
     * [collectCanvasHoisted] always computes it. Nothing in the code pinned
     * that ordering, so it was one refactor away from crashing too; a total
     * predicate makes the ordering irrelevant.
     */
    internal fun establishesUnescapableClip(properties: List<IRProperty>): Boolean =
        com.styleconverter.runtime.effects.clip.clipDecodeOrElse(
            where = "CanvasRootHoist.establishesUnescapableClip",
            // No readable clip ⇒ no `Modifier.clip` is emitted by the applier
            // that shares this decoder ⇒ there is nothing for an out-of-flow
            // descendant to escape, which is exactly what `false` asserts.
            fallback = false,
        ) {
            com.styleconverter.runtime.effects.clip.establishesUnescapableClip(properties)
        }

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
     *    regardless of positioned ancestors (css-position-3 §2.1; pin S3:
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
        // Wave 35 (lane B1) — does an ancestor establish a containing block
        // through a USED TRANSFORM (css-transforms-1 §3 / css-transforms-2
        // §6: transform / individual transform properties / perspective /
        // preserve-3d / will-change transform)? Such an ancestor claims BOTH
        // out-of-flow classes, so it vetoes the hoist for fixed AND absolute
        // descendants alike. Defaulted false so every pre-wave-35 call site
        // (and every hostless path) keeps the exact wave-17/18 truth table.
        hasTransformedAncestor: Boolean = false,
        // Wave 49 (lane A4) — does an ancestor paint a `clip-path`? Not a
        // containing-block question: css-masking-1 §5 clips the element AND
        // its descendants with no escape clause, while the overlay this
        // decision feeds is a SIBLING of that ancestor in the Compose tree,
        // so a hoisted box would leave the clip behind. Vetoes BOTH classes
        // (an unescapable clip does not grade by `position`). Defaulted
        // false so every pre-wave-49 call site — and every hostless path —
        // keeps the exact wave-17/18/35 truth table.
        hasClippingAncestor: Boolean = false,
    ): Boolean = if (hasClippingAncestor) false else when (positionTypeOf(properties)) {
        // Viewport-anchored (F1) UNLESS a transformed ancestor has taken over
        // the containing block — css-transforms-1 §3 (transform) plus
        // css-transforms-2 §7 (preserve-3d) / §8 (perspective) are the
        // rules that pull a fixed box back out of the viewport. Chromium-measured
        // (probes C/D/E in _diag35/laneB1/probe-chromium.mjs: fixed under
        // transform, under perspective and under preserve-3d all anchor at
        // the styled ancestor, never at the viewport). A no-inset fixed box
        // with no such ancestor keeps the wave-17 canvas-origin anchor.
        PositionType.FIXED -> !hasTransformedAncestor
        // ICB-anchored only without ANY containing-block ancestor — positioned
        // (F2, css-position-3 §3.1) or transformed (probe B) — and only when
        // an inset actually anchors it there (RC1 — see kdoc above).
        PositionType.ABSOLUTE ->
            !hasPositionedAncestor && !hasTransformedAncestor && hasAnyInset(properties)
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
        // Wave 49 (lane A4) — the two ancestry flags the clip veto needs to
        // reconstruct "would this box have hoisted?". Defaulted so every
        // pre-wave-49 caller (the harness's §8.3.1 gap fold asks only about
        // ROOT components, which have no ancestor of any kind) keeps the
        // exact wave-18 RC1 truth table.
        hasTransformedAncestor: Boolean = false,
        hasClippingAncestor: Boolean = false,
    ): Boolean =
        // Wave-18 RC1, verbatim: an all-auto-inset absolute box with no
        // positioned ancestor paints at its STATIC position, not the canvas
        // origin (css-position-3 §3.1).
        (
            positionTypeOf(properties) == PositionType.ABSOLUTE &&
                !hasPositionedAncestor &&
                !hasAnyInset(properties)
            ) ||
            // Wave-49 A4: the box the clip veto pulled back out of the
            // overlay. It must land in the SAME in-slot, zero-flow-footprint
            // mount RC1 owns — an out-of-flow box still reserves no space
            // (css-position-3 §2.1) — and its own PositionApplier offset then
            // places it from that slot. Gated on the hoist it WOULD have
            // taken (clip flag deliberately false in the probe), so a box
            // that already renders through the positioned-parent machinery
            // (RenderAbsoluteChild — it has a positioned ancestor, so it was
            // never a hoist candidate) is untouched.
            //
            // BLAST RADIUS, RE-DERIVED BY EXECUTION (wave 49 lane F1; the
            // wave-49 skeptic walk over all 1435 wave-48 per-test IR
            // documents, pinned here by ClipHoistCorpusCrashTest): the veto
            // changes the hoist/static decision on EXACTLY 2 of 1435
            // documents — clip-path-blending-offset and
            // clip-path-on-fixed-position-scroll (the latter scoreExcluded).
            // A4's census claimed the three css-masking corner tests
            // (circle-closest-corner, circle-farthest-corner,
            // ellipse-closest-farthest-corner) "have a positioned ancestor,
            // so were never hoist candidates". That is WRONG about which box
            // it describes: in all three the clip-path CARRIER is itself an
            // absolutely positioned root with insets and no positioned
            // ancestor, so it DOES hoist; it is the carrier's inner child
            // that has the positioned ancestor. Their decisions are unchanged
            // for a different reason — a box's own clip never feeds its own
            // ancestry flag — but they DO newly enter the clip predicate,
            // which is precisely where the wave-49 crash lived.
            (
                hasClippingAncestor &&
                    shouldHoistToCanvasRoot(
                        properties,
                        hasPositionedAncestor,
                        hasTransformedAncestor,
                        hasClippingAncestor = false,
                    )
                )

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
        // Wave 35 (lane B1) — the transform-CB ancestry, defaulted so every
        // pre-wave-35 caller keeps the exact wave-17 truth table.
        hasTransformedAncestor: Boolean = false,
        // Wave 49 (lane A4) — the unescapable-clip ancestry; same defaulting
        // rule, and it can only ever REMOVE an interception (the overlay's
        // mirror walk applies the identical veto).
        hasClippingAncestor: Boolean = false,
    ): Boolean = hostActive &&
        bypass !== component &&
        shouldHoistToCanvasRoot(
            component.properties, hasPositionedAncestor, hasTransformedAncestor,
            hasClippingAncestor,
        )

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
        // Wave 35 (lane B1) — the transform-CB half of the ancestry; see
        // [LocalHasTransformedAncestor] for why it is a SECOND flag.
        hasTransformedAncestor: Boolean = false,
        // Wave 49 (lane A4) — the THIRD ancestry flag; see
        // [LocalHasClippingAncestor]. Same defaulting rule as the second.
        hasClippingAncestor: Boolean = false,
    ): List<IRComponent> {
        // Accumulator in visit order (document order).
        val out = mutableListOf<IRComponent>()
        // Local recursion carrying ALL THREE ancestry flags per level, plus
        // the wave-49 (lane A7) multicol channel — null at the roots, because
        // a document root has no multi-column ancestor and the spanner rule
        // is inert until one appears.
        fun walk(
            node: IRComponent,
            ancestorPositioned: Boolean,
            ancestorTransformed: Boolean,
            ancestorClipped: Boolean,
            positionedAtMulticol: Boolean?,
        ) {
            // Collect the node itself when the shared decision says hoist.
            if (shouldHoistToCanvasRoot(
                    node.properties, ancestorPositioned, ancestorTransformed, ancestorClipped,
                )
            ) {
                out += node
            }
            // Children see a positioned ancestor if one already existed OR
            // this node is itself positioned (CSS 2.1 §10.1) — EXCEPT below a
            // `column-span: all` box, where css-multicol-1 §6.1 restarts the
            // chain at the multi-column container (wave 49, lane A7). The
            // whole decision is delegated so ComponentRenderer's composition
            // channel reads the identical function and the two can never
            // disagree about where the chain breaks.
            val childPositioned = com.styleconverter.runtime.columns
                .MulticolSpannerContainingBlock.childPositionedAncestor(
                    ancestorPositioned, positionedAtMulticol, node.properties,
                )
            // …and a transformed ancestor on the same OR-accumulating rule
            // (css-transforms-1 §3 — a used transform never un-establishes).
            // Deliberately NOT restarted at a spanner — see
            // MulticolSpannerContainingBlock.TRANSFORM_CHAIN_RESTART_UNIMPLEMENTED
            // for the measured reason (no corpus witness).
            val childTransformed =
                ancestorTransformed || establishesTransformContainingBlock(node.properties)
            // …and a clipping ancestor on the same OR-accumulating rule
            // (css-masking-1 §5 — a clip-path never un-clips a subtree).
            val childClipped =
                ancestorClipped || establishesUnescapableClip(node.properties)
            // A multi-column container stamps its own chain state here; every
            // other box passes the stamp through, so a spanner nested several
            // levels down still restarts from its own container.
            val childAtMulticol = com.styleconverter.runtime.columns
                .MulticolSpannerContainingBlock.childPositionedAtMulticol(
                    positionedAtMulticol, childPositioned, node.properties,
                )
            // Recurse in document order (children may be null on leaves).
            node.children?.forEach {
                walk(it, childPositioned, childTransformed, childClipped, childAtMulticol)
            }
        }
        // Roots start from the caller's ancestry context (canvas root: false)
        // and outside any multicol (null — the spanner rule cannot fire yet).
        roots.forEach {
            walk(it, hasPositionedAncestor, hasTransformedAncestor, hasClippingAncestor, null)
        }
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
        // Wave 35 (lane B1) — same second ancestry flag as the collect walk;
        // the two walks MUST stay one-for-one or activation and the overlay
        // list disagree.
        hasTransformedAncestor: Boolean = false,
        // Wave 49 (lane A4) — the third ancestry flag; the two walks MUST
        // stay one-for-one on it too, or a clip-vetoed box loses the host
        // that gives it its zero-flow anchor.
        hasClippingAncestor: Boolean = false,
    ): Boolean {
        // Local recursion carrying all three ancestry flags per level plus
        // the wave-49 (lane A7) multicol channel — the same threading as
        // collectCanvasHoisted's walk, one-for-one.
        fun walk(
            node: IRComponent,
            ancestorPositioned: Boolean,
            ancestorTransformed: Boolean,
            ancestorClipped: Boolean,
            positionedAtMulticol: Boolean?,
        ): Boolean {
            // Either out-of-flow class counts (see kdoc): hoisted overlay…
            if (shouldHoistToCanvasRoot(
                    node.properties, ancestorPositioned, ancestorTransformed, ancestorClipped,
                )
            ) {
                return true
            }
            // …or the in-slot zero-flow static-position class. Deliberately
            // NOT gated on the transform flag for the wave-18 RC1 clause: a
            // no-inset absolute box sits at its static position whichever
            // ancestor is its containing block (css-position-3 §3.1), so the
            // zero-flow anchor is right either way and the wave-21 activation
            // breadth is unchanged. The wave-49 clip clause DOES read both
            // extra flags, because it reconstructs the hoist it vetoed.
            if (rendersInFlowAsStaticPosition(
                    node.properties, ancestorPositioned, ancestorTransformed, ancestorClipped,
                )
            ) {
                return true
            }
            // Children see a positioned ancestor if one already existed OR
            // this node is itself positioned (CSS 2.1 §10.1) — through the
            // shared wave-49 helper, so the `column-span: all` chain restart
            // (css-multicol-1 §6.1) applies to the ACTIVATION walk exactly as
            // it does to the collect walk. If the two ever diverged, a box
            // could hoist with no host to anchor it.
            val childPositioned = com.styleconverter.runtime.columns
                .MulticolSpannerContainingBlock.childPositionedAncestor(
                    ancestorPositioned, positionedAtMulticol, node.properties,
                )
            // Likewise for the transform-CB flag (css-transforms-1 §3) and
            // the clip flag (css-masking-1 §5) — both keep the plain
            // OR-accumulation (see the collect walk for why).
            val childTransformed =
                ancestorTransformed || establishesTransformContainingBlock(node.properties)
            val childClipped =
                ancestorClipped || establishesUnescapableClip(node.properties)
            // The multicol stamp, published identically to the collect walk.
            val childAtMulticol = com.styleconverter.runtime.columns
                .MulticolSpannerContainingBlock.childPositionedAtMulticol(
                    positionedAtMulticol, childPositioned, node.properties,
                )
            // Recurse in document order (children may be null on leaves).
            return node.children?.any {
                walk(it, childPositioned, childTransformed, childClipped, childAtMulticol)
            } ?: false
        }
        // Roots start from the caller's ancestry context (canvas root: false)
        // and outside any multicol (null — the spanner rule cannot fire yet).
        return roots.any {
            walk(it, hasPositionedAncestor, hasTransformedAncestor, hasClippingAncestor, null)
        }
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
     * Wave 42 (lane W8) — the CSS2 §10.3.7 shrink-to-fit request for an
     * out-of-flow box whose `width` is AUTO.
     *
     * §10.3.7: with `width: auto` (and at least one auto horizontal inset)
     * the used width is `min(max(preferred minimum, available), preferred)`.
     * The pre-wave-42 anchors measured UNBOUNDED always, i.e. they used
     * `preferred` (the single-line max-content width) unconditionally —
     * measured on WPT `filter-effects/backdrop-filter-edge-pixels.html`
     * (android-ref 0.9211): the abspos `<p>` container's text runs off the
     * canvas UNWRAPPED while Chromium wraps it at the ICB edge; same
     * defect on `backdrop-filter-clip-rect.html`'s abspos prose div.
     *
     * @param widthAuto is the box's `width`/`inline-size` undeclared in the
     *   IR? Only then does §10.3.7's shrink-to-fit clause apply — a
     *   declared width must keep the unbounded measure so it can OVERFLOW
     *   its containing block (css-position-3 §2.1), never clamp to it.
     * @param availableX the §10.3.7 "available width" in the slot's own
     *   space, or null to derive it from the slot's incoming maxWidth (the
     *   RC1 static-position mount: its flow slot's constraint envelope IS
     *   the width the box would have resolved in flow, which §10.3.7's
     *   static-position case prescribes).
     */
    data class ShrinkToFitSpec(
        val widthAuto: Boolean,
        val availableX: Dp? = null,
    )

    /**
     * Is `width` auto for shrink-to-fit purposes? Pure IR predicate
     * (JVM-pinned): true when the declaration list carries NO physical
     * `Width` and NO logical `InlineSize` (the two IR spellings that set a
     * used inline extent; min/max clamps leave the width AUTO per
     * css-sizing-3 §5). Deliberately conservative: ANY declared width —
     * px, %, calc, keyword — keeps the frozen unbounded measure, because
     * §10.3.7's first clause ("If width is not auto…") uses the declared
     * value and overflow is then the correct rendering.
     */
    internal fun widthIsAuto(properties: List<IRProperty>): Boolean =
        properties.none { it.type == "Width" || it.type == "InlineSize" }

    /**
     * §10.3.7's "available width" for a canvas-anchored box: the ICB width
     * minus the DECLARED horizontal insets (an auto inset contributes
     * nothing; the spec's solve-for-auto treats it as the remainder). The
     * subtraction is SIGNED — a negative `left` genuinely widens the space
     * to the right edge — and the result floors at 0 (a box anchored past
     * the far edge has no available span, not a negative one). Pure Dp
     * math, JVM-pinned in CanvasRootHoistTest.
     */
    internal fun shrinkAvailableWidth(canvasWidth: Dp, start: Dp?, end: Dp?): Dp {
        // Remainder after both declared insets (absent side = 0 taken).
        val remainder = canvasWidth.value - (start?.value ?: 0f) - (end?.value ?: 0f)
        // Floor at zero — Constraints cannot carry a negative bound.
        return Dp(remainder.coerceAtLeast(0f))
    }

    /**
     * The measure-time max-width band for a shrink-to-fit box, in px:
     * `max(preferred minimum, available)`. Feeding that as maxWidth
     * reproduces §10.3.7's full clamp — content narrower than the band
     * sizes to its preferred width (the `min(…, preferred)` half falls out
     * of content-sized measurement), content wider WRAPS at the band, and
     * the band never squeezes below the preferred minimum (a fixed-width
     * child keeps its declared size and overflows, exactly like the
     * unbounded measure did). Null preferredMin = the intrinsic channel
     * refused ([IntrinsicChannel.probe] — a subcomposed descendant); the
     * caller then keeps the FROZEN unbounded measure, which mis-wraps one
     * box instead of squeezing an unknowable minimum. Pure, JVM-pinned.
     */
    internal fun shrinkToFitMaxPx(preferredMinPx: Int?, availablePx: Int): Int? =
        preferredMinPx?.let { kotlin.math.max(it, availablePx) }

    /**
     * Zero-flow anchor — the ONE measurement wrapper both out-of-flow
     * mounting modes share: measure the component UNBOUNDED
     * (Constraints() == 0..∞ — its own width/height modifiers decide, the
     * same rationale as the wave-8 absposOverflowMeasure) UNLESS a
     * [ShrinkToFitSpec] engages §10.3.7's width clamp (see above), report
     * 0×0 (see [hoistedFlowReportPx]) so the box occupies NO flow space
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
        // Wave 42 (lane W8) — §10.3.7 shrink-to-fit for a width:auto box.
        // Default null keeps every pre-wave-42 call site (and every
        // declared-width box) on the frozen unbounded measure.
        shrinkToFit: ShrinkToFitSpec? = null,
        // Wave 46 (lane Y6) — the SLOT-relative end anchor for the
        // positioned-parent Column mount (PositionedParentFlowSlot): an axis
        // whose only inset is `right`/`bottom` anchors at the slot's own
        // bounded max constraint. On the INLINE axis that is the containing
        // block's content width. On the BLOCK axis Compose's Column hands a
        // non-weighted child the REMAINING main-axis space whenever the
        // Column's own maxHeight is finite — which equals the containing
        // block's content height exactly when no in-flow sibling precedes
        // the child (every preceding out-of-flow one reports 0×0 through
        // this same anchor), and is short by their height otherwise. A
        // genuinely unbounded axis ⇒ null ⇒ A4 start anchor, as before; the
        // full caveat table is PositionedParentFlowSlot's header. Only read
        // when the explicit
        // endEdgeX/endEdgeY above are null, so the overlay's canvas-extent
        // anchor keeps precedence; default null keeps every pre-wave-46
        // call site byte-identical.
        slotEndAnchor: PositionConfig? = null,
    ): Modifier = Modifier.layout { measurable, constraints ->
        // Measure constraints: unbounded by default (the box is sized by
        // its own properties alone) — narrowed to the §10.3.7 band ONLY
        // when a spec says width is auto AND an available width is known.
        val childConstraints = if (shrinkToFit?.widthAuto == true) {
            // Available width: the caller-computed ICB remainder (overlay
            // slots), else this slot's own incoming maxWidth when bounded
            // (the RC1 static-position flow slot).
            val availablePx = shrinkToFit.availableX?.roundToPx()
                ?: constraints.maxWidth.takeIf { constraints.hasBoundedWidth }
            // The preferred minimum (§10.3.7 = min-content width), read
            // through the guarded channel: subcomposed descendants REFUSE
            // intrinsics with a throw that would kill the whole capture
            // composition (see IntrinsicChannel's banner), so a refusal
            // logs once and keeps the frozen unbounded measure.
            val preferredMinPx = availablePx?.let {
                com.styleconverter.runtime.layout.IntrinsicChannel.probe(
                    "CanvasRootHoist",
                    "abspos shrink-to-fit (§10.3.7) skipped for one box — " +
                        "no intrinsic channel; it keeps its unbounded " +
                        "preferred width.",
                ) { measurable.minIntrinsicWidth(Constraints.Infinity) }
            }
            // max(preferred-min, available) as the wrap band, or the
            // frozen unbounded measure when either input is unknowable.
            val maxPx = availablePx?.let { shrinkToFitMaxPx(preferredMinPx, it) }
            if (maxPx != null) Constraints(maxWidth = maxPx) else Constraints()
        } else Constraints()
        val placeable = measurable.measure(childConstraints)
        // Wave 46 (lane Y6) — the slot-relative end edges, resolved INSIDE
        // the measure (the slot's constraints are only known here). The
        // inline axis is direction-aware through the same predicate the
        // nested Box mount applies (a logical-only inline-end inset under
        // RTL withdraws to the start anchor — PositionedAncestorAnchor's
        // rationale); the block axis reads the physical/logical fold as is.
        val slotEndX: Float? = slotEndAnchor?.let { cfg ->
            PositionedParentFlowSlot.slotEndEdgePx(
                PositionedAncestorAnchor.inlineAxisAnchorsFromEnd(
                    cfg, layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl,
                ),
                if (constraints.hasBoundedWidth) constraints.maxWidth else null,
            )
        }
        val slotEndY: Float? = slotEndAnchor?.let { cfg ->
            PositionedParentFlowSlot.slotEndEdgePx(
                cfg.anchorsFromEndY,
                if (constraints.hasBoundedHeight) constraints.maxHeight else null,
            )
        }
        // Report zero on both axes: no flow/canvas growth from the ink.
        layout(hoistedFlowReportPx(), hoistedFlowReportPx()) {
            // Anchor at the containing block's START corner (start-anchored
            // / static-position boxes: the child's own inset offset does the
            // rest — F1/F2 anchor semantics, insets not deltas), or flush
            // with its END edge when the box declares only `right`/`bottom`
            // (A5). The end edge is measured FROM the same origin, so both
            // branches share the one translation. Dp→px here, inside the
            // Density receiver, so the arithmetic is in the same device-px
            // space as placeable.width/height at any screen density. The
            // explicit canvas end edge (overlay slots) wins over the
            // slot-relative one (wave 46); both null ⇒ the start anchor.
            placeable.place(
                x = anchorPlacePx(originX.roundToPx(), endEdgeX?.toPx() ?: slotEndX, placeable.width),
                y = anchorPlacePx(originY.roundToPx(), endEdgeY?.toPx() ?: slotEndY, placeable.height),
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
     * viewport, css-position-3 §2.1; ICB-anchored absolute → the initial
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
            // Wave 42 (lane W8): §10.3.7 shrink-to-fit for a width:auto
            // hoisted box. Available = ICB width minus the DECLARED
            // horizontal insets (the same resolved sides the anchor rule
            // table reads, so anchor and clamp cannot disagree). A caller
            // without canvas geometry (A4) passes no spec and keeps the
            // frozen unbounded measure.
            shrinkToFit = if (canvasWidth != null && widthIsAuto(properties)) {
                ShrinkToFitSpec(
                    widthAuto = true,
                    availableX = shrinkAvailableWidth(
                        canvasWidth, config.resolvedStart, config.resolvedEnd,
                    ),
                )
            } else null,
        )
    }

    /**
     * Wave 42 (lane W8) — the [ShrinkToFitSpec] for ComponentRenderer's
     * RC1 static-position mount: width-auto boxes wrap at their flow
     * slot's OWN constraint envelope (availableX = null → the anchor reads
     * the incoming maxWidth, which for a static-position box IS §10.3.7's
     * containing-block remainder). Declared-width boxes return null and
     * keep the frozen unbounded measure. Lives here rather than at the
     * call site so the whole shrink-to-fit decision stays in one file with
     * its truth table and pins.
     */
    fun staticPositionShrinkToFit(properties: List<IRProperty>): ShrinkToFitSpec? =
        if (widthIsAuto(properties)) ShrinkToFitSpec(widthAuto = true) else null

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
        // css-position-3 §2.1; ICB-anchored absolute → the initial
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
