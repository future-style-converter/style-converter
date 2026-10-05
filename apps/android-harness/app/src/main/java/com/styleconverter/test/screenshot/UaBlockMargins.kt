package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent
// Wave 25 (lane UAM): the single Kotlin owner of the UA vertical table —
// shared with the runtime's block-CHILD fold so root and descendant agree.
import com.styleconverter.runtime.spacing.uaVerticalBlockMargins
// wave-46 lane Y8: the single owner of the UA margin's FONT BASIS — the
// root's own computed font-size the em table resolves against.
import com.styleconverter.runtime.spacing.UaBlockMarginFontBasis
// wave-52 lane L2: the per-root composed-stack PLAN builder below reads the
// SAME renderer decision functions the Column and the hoist Host run (one
// decision, two consumers), and the runtime's px leaf reader for the
// self-collapsing predicate.
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.layout.position.CanvasRootHoist
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToInt

/**
 * Pure user-agent default block-margin model for the COMPOSED WPT capture
 * (TITAN Round 4b, FIX 1). No Compose/Android runtime — fully JVM-testable
 * (see UaBlockMarginsTest).
 *
 * ## Why this exists
 * The composed canvas (ScreenshotCaptureScreen.ComposedCaptureCanvas) stacks
 * every WPT test's roots in a Column. The Chromium browser-ref
 * (tools/titan/capture-browser-ref.mjs) renders the reference page with its FULL
 * UA stylesheet intact, so a `<p>` bar keeps its 1em (≈16px @16px root) block
 * margins and adjacent bars COLLAPSE to a single ~16px gap. Compose has no UA
 * stylesheet and no margin collapsing, so the composed bars render FLUSH and a
 * 10-bar test (background-color-hsl-001 …) scores far below the ref.
 *
 * The web fix reverts the composed elements' margins to the UA origin
 * (`margin: revert`) and lets block-flow collapsing happen natively. Natives
 * have no UA sheet to revert to, so we EMULATE the same values + collapsing here
 * and inject them as vertical spacing between the stacked roots.
 *
 * Two CSS rules this model reproduces:
 *  - An IR-declared margin WINS over the UA default (UA is the lowest-priority
 *    origin) — [effectiveUaMargins] zeroes any side the IR declares, because the
 *    runtime's margin applier already renders that side.
 *  - Adjacent vertical margins COLLAPSE to their max (CSS 2.1 §8.3.1) —
 *    [collapsedVerticalGaps]. Two 16px-margin bars → a single 16px gap.
 */

/** UA default block margins in px (at a 16px root font — or, wave-46 Y8,
 *  at the root's OWN computed size when it carries one; whole px because
 *  the composed canvas's gap Spacers are placed at integer px anyway). */
data class UaMargins(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    companion object { val ZERO = UaMargins(0, 0, 0, 0) }
}

/**
 * The UA stylesheet's default block margins for a source tag, at a 16px root
 * font — the exact per-tag values the browser-ref's `<p>`/`<h*>`/… render with
 * (CSS 2.1 §D.2 default sheet, calibrated to the browser-ref this round diffs
 * against). Tags with no block margin in the UA sheet (div/section/article/
 * header/footer/main/nav/aside) and unknown/null tags return [UaMargins.ZERO].
 */
fun uaBlockMargins(sourceTag: String?, ownFontSizePx: Float? = null): UaMargins {
    // Wave 25 (lane UAM): the VERTICAL half is no longer duplicated here.
    // The runtime owns the ONE Kotlin copy of the table (spacing/
    // UaBlockChildMargins.uaVerticalBlockMargins) because the block-CHILD
    // fold in ComponentRenderer needs it too — a root and a descendant
    // must never disagree about what a `<p>` margin is worth. Values are
    // unchanged (p 16 · h1 21 · h2 19 · h3 16 · h4 21 · h5 27 · h6 37 ·
    // ul/ol/blockquote/pre/figure 16 · everything else 0), so every
    // Round-4 / RC-A4 pin in UaBlockMarginsTest keeps its number.
    // wave-46 lane Y8: [ownFontSizePx] (null = no own font signal → the
    // table verbatim) is the em basis of css-values-4 §6.1.1 — a root
    // `<p>` with `font-size: larger` gets 1em × 19.2 = 19.2 → 19px, the
    // browser-ref's row (inherit-computed-001 sat 3px high at 16). Rounded
    // to whole px: Chromium snaps the box edge, and 19.2 lands on row 35
    // exactly as the frozen ref does.
    val (top, bottom) = uaVerticalBlockMargins(sourceTag, ownFontSizePx)
    // The HORIZONTAL half stays local: only blockquote and figure carry a
    // UA inline inset (`margin: 1em 40px`), and §8.3.1 collapses the block
    // axis only — so the child fold has no use for these and the runtime
    // table deliberately omits them.
    val inline = when (sourceTag?.lowercase()) {
        "blockquote", "figure" -> 40
        else -> 0
    }
    return UaMargins(top.roundToInt(), bottom.roundToInt(), inline, inline)
}

/**
 * Map from a margin IR-property type name to the physical side(s) it defines.
 * Logical properties collapse to physical sides in the LTR-normalized engine
 * (block = top/bottom, inline = left/right); the shorthands expand to their set.
 */
private val MARGIN_SIDE_TYPES: Map<String, Set<String>> = mapOf(
    "MarginTop" to setOf("top"),
    "MarginBottom" to setOf("bottom"),
    "MarginLeft" to setOf("left"),
    "MarginRight" to setOf("right"),
    "MarginBlockStart" to setOf("top"),
    "MarginBlockEnd" to setOf("bottom"),
    "MarginInlineStart" to setOf("left"),
    "MarginInlineEnd" to setOf("right"),
    "MarginBlock" to setOf("top", "bottom"),
    "MarginInline" to setOf("left", "right"),
    "Margin" to setOf("top", "bottom", "left", "right"),
)

/** Which physical sides the given IR property types declare a margin for. */
fun declaredMarginSides(propertyTypes: Collection<String>): Set<String> {
    val out = mutableSetOf<String>()
    for (t in propertyTypes) MARGIN_SIDE_TYPES[t]?.let { out.addAll(it) }
    return out
}

/**
 * The UA default margins with any IR-declared side zeroed out — because an
 * IR-declared margin (higher-priority origin) already renders through the
 * runtime's margin applier and MUST win over the UA default. Pure/testable.
 */
fun effectiveUaMargins(
    sourceTag: String?,
    declaredSides: Set<String>,
    // wave-46 Y8: the root's own computed font-size (null = 16px table).
    ownFontSizePx: Float? = null,
): UaMargins {
    val ua = uaBlockMargins(sourceTag, ownFontSizePx)
    return UaMargins(
        top = if ("top" in declaredSides) 0 else ua.top,
        bottom = if ("bottom" in declaredSides) 0 else ua.bottom,
        left = if ("left" in declaredSides) 0 else ua.left,
        right = if ("right" in declaredSides) 0 else ua.right,
    )
}

/** [effectiveUaMargins] for a decoded component (reads `_tag` + property
 *  types, and — wave-46 Y8 — its own FontSize for the em basis: a composed
 *  root is a body-level child, so its inherited size is the UA 16px default
 *  and [UaBlockMarginFontBasis.ownFontSizePx]'s default base is exact).
 *  The tag rides into the basis too: it gates the monospace fixed-default
 *  rung, which Blink applies only to KEYWORD-sized elements — an em-sized
 *  heading (h1/h2/h3/h5/h6) keeps the table instead of a wrong 13px em. */
fun effectiveUaMargins(component: IRComponent): UaMargins =
    effectiveUaMargins(
        component._tag,
        declaredMarginSides(component.properties.map { it.type }),
        UaBlockMarginFontBasis.ownFontSizePx(component.properties, sourceTag = component._tag),
    )

/**
 * Collapsed vertical GAPS to insert around a vertical stack of blocks whose
 * outermost edges sit against a PADDED container (the composed canvas has 16dp
 * padding, and padding blocks a box's margin from collapsing with its parent —
 * so the first block's top margin and the last block's bottom margin are kept
 * in full, NOT collapsed away).
 *
 * @param margins ordered `(topMargin, bottomMargin)` per block, top-to-bottom.
 * @return `n + 1` gaps: `[beforeFirst, between0-1, between1-2, …, afterLast]`.
 *   Between two blocks the gap is the MAX of the lower block's bottom margin and
 *   the upper block's top margin (positive-margin collapsing, CSS 2.1 §8.3.1).
 *   Negative margins are out of scope (UA defaults are all positive) and are
 *   floored at 0.
 */
fun collapsedVerticalGaps(margins: List<Pair<Int, Int>>): List<Int> =
    // Delegate to the float fold (RC-A4) so there is exactly ONE §8.3.1 gap
    // rule; inputs are whole px, so the round-trip through Float is lossless.
    collapsedVerticalGapsPx(margins.map { it.first.toFloat() to it.second.toFloat() })
        .map { it.toInt() }

/**
 * Float twin of [collapsedVerticalGaps] (RC-A4, wave 19): IR-declared margins
 * arrive from the runtime's margin classifier as px FLOATS, so the fold that
 * mixes them with the (integer) UA defaults runs in float px. Same contract:
 * `n + 1` gaps, first/last preserved (the canvas padding blocks parent
 * collapse), interior gaps collapsed to the max of the abutting margins
 * (CSS 2.1 §8.3.1 positive-margin rule; negatives floored at 0 — out of scope).
 */
fun collapsedVerticalGapsPx(margins: List<Pair<Float, Float>>): List<Float> =
    // Delegate to the transparency-aware fold (wave-19 follow-up) with every
    // entry OPAQUE — for all-opaque stacks the two rules are provably
    // identical (each opaque root closes the adjoining set, so every
    // interior gap is max(prev bottom, own top) exactly as before), keeping
    // the FIX 1 / RC-A4 pins byte-stable while §8.3.1 lives in ONE place.
    collapsedRootStackGapsPx(margins.map { RootStackMargin(it.first, it.second, stripDeclared = false) })

/**
 * Wave-19 follow-up — the ONE §8.3.1 gap rule, now aware of margin-
 * TRANSPARENT roots (zero flow footprint — see [RootStackMargin.marginTransparent]).
 *
 * CSS 2.1 §8.3.1 model implemented here:
 *  - Adjoining vertical margins collapse to the MAX of the set (positive
 *    margins only — negatives are out of the lane's scope and floored at 0,
 *    §8.3.1's negative rules are not emulated).
 *  - A box that occupies no flow space does NOT close the adjoining set:
 *    for an empty in-flow box "margins collapse through it", and an
 *    out-of-flow box is absent from the flow entirely (§9.3.1) — either
 *    way the previous opaque root's bottom margin, every transparent
 *    root's own (usually zero) margins, and the next opaque root's top
 *    margin form ONE adjoining set resolving to ONE max() gap.
 *  - Gap PLACEMENT follows §8.3.1's collapse-through position rule ("the
 *    position of the element's top border edge is the same as it would
 *    have been if ... its top margin were collapsed only with PRECEDING
 *    margins"): the gap emitted above each transparent root is the prefix
 *    max of the set up to and including its own top margin, minus what the
 *    set already emitted — so a transparent root's slot (its static-
 *    position ink anchor) lands exactly where the hypothetical in-flow box
 *    would, and the amounts always sum to the full set max by the time the
 *    next opaque root mounts.
 *  - First/last gaps stay preserved (the canvas's 16dp padding blocks
 *    parent↔child collapse — same rationale as the FIX 1 fold), including
 *    when the stack STARTS or ENDS with transparent roots: the padding
 *    still bounds the set on that side.
 *
 * Contract unchanged: `n + 1` gaps `[beforeFirst, between…, afterLast]`,
 * index-aligned with the composed Column's Spacers.
 */
fun collapsedRootStackGapsPx(plans: List<RootStackMargin>): List<Float> {
    // Empty stack: the single "gap" slot the FIX 1 contract always emitted.
    if (plans.isEmpty()) return listOf(0f)
    // One gap above each root plus the trailing gap after the last.
    val gaps = FloatArray(plans.size + 1)
    // Max of the currently-OPEN adjoining-margin set (§8.3.1 "maximum of
    // the adjoining margin widths"). Starts at 0: the canvas padding edge
    // contributes no margin of its own.
    var runningMax = 0f
    // How much of the open set's max has ALREADY been emitted as gaps —
    // nonzero only while collapsing THROUGH transparent roots, so the set's
    // total contribution is emitted exactly once, never doubled.
    var emitted = 0f
    for (i in plans.indices) {
        // This root's top margin joins the open set (negatives floored — the
        // §8.3.1 negative-margin rules are out of the emulation's scope).
        runningMax = maxOf(runningMax, plans[i].topPx.coerceAtLeast(0f))
        // Gap above root i: the set's prefix max through this root's top
        // margin, minus what the set already emitted (the collapse-through
        // position rule — see kdoc). For an opaque root after an opaque
        // root, emitted is 0 and this is exactly max(prev bottom, own top).
        gaps[i] = runningMax - emitted
        if (plans[i].marginTransparent) {
            // Zero-flow root: the set stays OPEN across it (§8.3.1
            // collapse-through / §9.3.1 out-of-flow absence). Record what
            // was emitted, then let its own bottom margin join the set —
            // zero for hoisted overlay roots, whose abspos margins never
            // collapse into flow; the declared px for a stripped
            // static-position root (the hypothetical-box model).
            emitted += gaps[i]
            runningMax = maxOf(runningMax, plans[i].bottomPx.coerceAtLeast(0f))
        } else {
            // Opaque root: its border box separates the margins — the set
            // CLOSES here and a fresh one opens with its bottom margin.
            runningMax = plans[i].bottomPx.coerceAtLeast(0f)
            emitted = 0f
        }
    }
    // Trailing gap: whatever the still-open set has not yet emitted — the
    // last opaque root's full bottom margin (preserved, padding blocks the
    // parent collapse) less anything already emitted while collapsing
    // through trailing transparent roots.
    gaps[plans.size] = runningMax - emitted
    return gaps.toList()
}

/**
 * RC-A4 (wave 19) — one stacked root's resolved BLOCK margins for the
 * composed canvas's vertical flow, plus whether the root's DECLARED block
 * margins must be STRIPPED from its own render.
 *
 * FIX 1 above deferred to IR-declared margins (UA zeroed, root renders its
 * own) — but two DECLARED margins on ADJACENT roots then both rendered in
 * full and STACKED, where the browser collapses them to their max (CSS 2.1
 * §8.3.1; flex-abspos-staticpos-align-self-safe-001: 20px+20px gave a 96px
 * root pitch vs the ref's collapsed 76px, drifting every later root +20px).
 * The fix: statically-resolvable declared block margins are folded INTO the
 * same collapsed-gap math as the UA defaults, and stripped from the root's
 * render via the runtime's §8.3.1 override channel (LocalCollapsedMargin).
 */
data class RootStackMargin(
    // Effective top margin feeding the collapsed gap ABOVE this root, px.
    val topPx: Float,
    // Effective bottom margin feeding the collapsed gap BELOW this root, px.
    val bottomPx: Float,
    // True → the root's declared block margins moved into the gaps: zero them
    // on the root's own render (inline/left/right margins stay untouched).
    val stripDeclared: Boolean,
    // Wave-19 follow-up (collapse-through): true for a root with ZERO flow
    // footprint in the composed stack — the wave-17 canvas-hoisted roots
    // (intercepted in flow, ink in the overlay) and the wave-18 RC1
    // static-position roots (mounted at 0×0 via zeroFlowAnchor). CSS 2.1
    // §8.3.1 collapses vertical margins THROUGH a box that takes no space
    // in the flow ("margins collapse through it" for empty boxes; out-of-
    // flow boxes are simply absent from the adjoining chain, §9.3.1), so
    // the fold must keep the adjoining-margin set OPEN across this root
    // instead of closing it — two 20px neighbors give ONE 20px gap, not
    // max(20,0)+max(0,20)=40. Default false keeps every pre-existing
    // caller/pin byte-identical (an opaque root closes the set as before).
    val marginTransparent: Boolean = false,
)

/**
 * Wave 26 (lane RES residual 3a) — fold a root's HOIST BAND into its stack
 * contribution.
 *
 * ## The double count
 * A composed root's outer block spacing had TWO independent owners: this
 * root-stack fold (a Spacer between roots) and the renderer's own
 * `BlockCollapsePlan.hoistTopPx/hoistBottomPx` band (transparent padding
 * outside the root's border box, for the first/last child's margin that
 * escapes through an open parent edge). Both are outer spacing in the SAME
 * adjoining-margin region, so they ADDED where CSS 2.1 §8.3.1 takes ONE
 * max() over the whole chain. With a previous root ending in a 40px bottom
 * margin, a `<blockquote>` root (UA 16) whose first child is an `<h5>` (UA
 * 27) rendered 40 (fold) + 11 (band) = 51px against the browser's
 * max(40, 16, 27) = 40.
 *
 * ## The model (ONE owner)
 * The band is `max(rootOwnEdge, childEdge) − rootOwnEdge`, so
 * `rootOwnEdge + band` is exactly the root's COLLAPSED-THROUGH edge. Adding
 * the band here makes this fold see that collapsed edge and resolve the whole
 * chain with its existing n-ary max; the renderer is told to emit no band for
 * this root (BlockMarginCollapse.LocalHoistBandSuppressedFor). Subtracting
 * the band from the emitted gap instead CANNOT work: two adjacent band-
 * carrying roots (A's last child bottom 16, B's first child top 16) would
 * need gap = 16 − 16 − 16 = −16, which floors at 0 and renders 32.
 *
 * Margin-TRANSPARENT roots are returned unchanged: they occupy no flow space
 * (hoisted to the canvas overlay, or the RC1 static-position 0×0 anchor), so
 * whatever band their own subtree emits never displaces flow siblings and
 * must not enter the gap math.
 *
 * @param band the root's `(hoistTopPx, hoistBottomPx)` from the runtime's
 *   ComponentRenderer.composedRootHoistBand — the SAME plan the renderer
 *   would have painted, never a re-derivation.
 */
fun withHoistBand(plan: RootStackMargin, band: Pair<Float, Float>): RootStackMargin =
    // Zero-flow roots never contribute to the stack gaps at all (see kdoc).
    if (plan.marginTransparent) plan
    // Opaque root: its stack edges become the collapsed-through values.
    else plan.copy(topPx = plan.topPx + band.first, bottomPx = plan.bottomPx + band.second)

/**
 * Resolve one in-flow root's stack contribution. Pure — pinned in
 * UaBlockMarginsTest (R1–R7), byte-parallel with the Swift twin
 * (UABlockMargin.rootStackMargin).
 *
 * @param declaresTop/@param declaresBottom whether the IR declares that block
 *   side (any Margin* type covering it — [declaredMarginSides]).
 * @param staticDeclaredPx the declared (top, bottom) px from the runtime's
 *   §8.3.1 classifier (BlockMarginCollapse.blockMarginsOrNull) — the SAME
 *   extractor the renderer paints with, so strip and render cannot disagree.
 *   Null when any block side is auto / negative / relative / calc (out of the
 *   static scope) OR when the root is out of flow (its margins never collapse,
 *   §8.3.1's in-flow precondition) — both bail to the FIX 1 behavior.
 * @param ownFontSizePx wave-46 Y8: the root's own computed font-size, the em
 *   basis of its UA default ([UaBlockMarginFontBasis.ownFontSizePx]); null
 *   (no own font signal — every R1-R7 pin, every corpus root without a
 *   FontSize) keeps the 16px-root table byte-identical.
 */
fun rootStackMargin(
    sourceTag: String?,
    declaresTop: Boolean,
    declaresBottom: Boolean,
    staticDeclaredPx: Pair<Float, Float>?,
    ownFontSizePx: Float? = null,
): RootStackMargin {
    // UA defaults for this tag (per-side deferral handled below), resolved
    // against the root's own font basis when it has one.
    val ua = uaBlockMargins(sourceTag, ownFontSizePx)
    // R1 — no declared block margin: pure UA contribution, FIX 1 unchanged.
    if (!declaresTop && !declaresBottom) {
        return RootStackMargin(ua.top.toFloat(), ua.bottom.toFloat(), stripDeclared = false)
    }
    // R4/R5 — declared but not statically resolvable (or out-of-flow root):
    // today's behavior — declared sides render via MarginApplier (UA zeroed,
    // stacking uncorrected), undeclared sides keep the UA default.
    if (staticDeclaredPx == null) {
        return RootStackMargin(
            if (declaresTop) 0f else ua.top.toFloat(),
            if (declaresBottom) 0f else ua.bottom.toFloat(),
            stripDeclared = false,
        )
    }
    // R2/R3 — static declared margins: each declared side contributes its
    // DECLARED px to the gap fold (author > UA, css-cascade-4 §6.1) and the
    // root's block margins are stripped; undeclared sides keep the UA default.
    return RootStackMargin(
        if (declaresTop) staticDeclaredPx.first else ua.top.toFloat(),
        if (declaresBottom) staticDeclaredPx.second else ua.bottom.toFloat(),
        stripDeclared = true,
    )
}

// ─────────────────────────────────────────────────────────────────────────
// wave-52 lane L2 — the per-root COMPOSED-STACK PLAN, extracted from
// ScreenshotCaptureScreen's `rootPlans` lambda so it is JVM-pinnable on the
// verbatim per-test IR (UaBlockMarginsTest U1–U6), byte-parallel with the
// Swift twin (UABlockMargin.composedRootStackPlan).
// ─────────────────────────────────────────────────────────────────────────

/**
 * True when [root] is the wave-18 RC1 STATIC-POSITION root under an ACTIVE
 * hoist host: an absolute box with no positioned ancestor and no inset,
 * which the renderer mounts in its Column slot at 0×0 (zeroFlowAnchor).
 * Same renderer decision functions the Host runs — never re-derived here.
 * Two consumers: the plan below (margin transparency) and the Column's
 * Appendix E step-8 z-order wrap (wave-52 L2 T3).
 */
fun isComposedStaticPositionRoot(root: IRComponent, hostActive: Boolean): Boolean =
    hostActive &&
        CanvasRootHoist.rendersInFlowAsStaticPosition(root.properties, hasPositionedAncestor = false)

/** IR property types whose mere PRESENCE makes a box paint as its own layer
 *  at CSS 2.1 Appendix E step 8 or later — a z-ordered box or a stacking
 *  context (css-transforms-1 §6, css-masking-1 §1, filter-effects-1 §2,
 *  compositing-1 §3/§4, css-contain-2 §3, css-will-change-1 §2, css-view-
 *  transitions-1). Presence alone counts, value unread (`opacity: 1` still
 *  blocks): a missed lift keeps the wave-51 Column order, a wrong lift paints
 *  the wrong box on top — the asymmetry the skeptic's 22 cells measured. */
private val PAINT_LAYER_TYPES = setOf(
    "ZIndex", "Opacity", "Transform", "Translate", "Rotate", "Scale", "Perspective",
    "TransformStyle", "Filter", "BackdropFilter", "ClipPath", "MaskImage", "MixBlendMode",
    "Isolation", "WillChange", "Contain", "ContainerType", "ViewTransitionName",
)

/** True when [c] or any composed descendant paints at Appendix E step 8+:
 *  a positioned box (`position` other than static, css-position-3 §2) or a
 *  [PAINT_LAYER_TYPES] layer. Such content follows an earlier RC1 root in
 *  TREE order at step 8, so it must stay ABOVE it — which a Column-level
 *  zIndex on the RC1 root cannot express (it lifts past the whole later
 *  subtree: CSS2/abspos/static-inside-inline-001's nested abspos green). */
private fun paintsAsLayer(c: IRComponent): Boolean {
    // The root-level Position leaf — the wire is the bare enum string.
    val position = (c.properties.firstOrNull { it.type == "Position" }?.data as? JsonPrimitive)
        ?.contentOrNull?.uppercase()
    // Any non-static position is a step-8 (or z-ordered) box.
    if (position != null && position != "STATIC") return true
    // A layer-creating property anywhere on this box.
    if (c.properties.any { it.type in PAINT_LAYER_TYPES }) return true
    // Recurse through the composed subtree (SlotComposer's children).
    return c.children.orEmpty().any(::paintsAsLayer)
}

/**
 * wave-52 lane L2 (T3, skeptic must-fix) — per composed root, whether the
 * Column lifts its DRAW above the in-flow roots (`Box(Modifier.zIndex(1f))`
 * in ScreenshotCaptureScreen). CSS 2.1 Appendix E step 8 paints a positioned
 * box with `z-index: auto` AFTER in-flow, non-positioned content, so
 * css-flexbox/align-items-007's abspos green must cover the later red `<img>`
 * (wave51-fix android f 0.9966, colour-vetoed). A root is lifted only when:
 *  1. it is the RC1 static-position slot ([isComposedStaticPositionRoot]);
 *  2. it declares NO z-index — a declared value is its own stacking level
 *     (step 3 below in-flow blocks when negative, step 9 otherwise), already
 *     on the box's chain via PositionApplier, and an outer wrapper z would
 *     shadow it (the runtime's ComponentRenderer.autoZForPositionedChild rule).
 *     Through the first cut every RC1 root was lifted, so 11 corpus tests'
 *     red `z-index: -1` box (css-tables/height-distribution/extra-height-
 *     given-to-all-row-groups-001 …) rose above the green it hides behind;
 *  3. it is a single painted box — no children, text, runs or generated
 *     content (deliberately NARROW: the one target is such a box, and a
 *     content-bearing root brings its descendants' own paint-order and
 *     display questions — css-cascade/unset-val-002's `display: unset` span);
 *  4. no LATER Column root that paints in the flow ([CanvasRootHoist]
 *     hoisted roots paint in the overlay, outside this order) carries
 *     step-8+ content ([paintsAsLayer]) — unless that root is itself lifted,
 *     since equal zIndex keeps tree order. Walked back to front for that.
 * Every unlifted root keeps the wave-51 Column order, byte-identical.
 */
fun composedRootsPaintingAboveFlow(roots: List<IRComponent>, hostActive: Boolean): List<Boolean> {
    // Filled back to front: rule 4 reads the verdicts of LATER roots.
    val lifted = BooleanArray(roots.size)
    for (i in roots.indices.reversed()) {
        val root = roots[i]
        // Rules 1–3: the RC1 slot, no declared z (the runtime's own reader),
        // and a single painted box.
        val candidate = isComposedStaticPositionRoot(root, hostActive) &&
            com.styleconverter.runtime.core.placement.ItemPlacementExtractor.zIndex(root.properties) == null &&
            root.children.isNullOrEmpty() && root._text.isNullOrEmpty() &&
            root.runs.isNullOrEmpty() && root.pseudos.isNullOrEmpty()
        // Rule 4: every later in-Column root is lifted too or plain flow.
        lifted[i] = candidate && (i + 1 until roots.size).none { j ->
            !lifted[j] &&
                !CanvasRootHoist.shouldHoistToCanvasRoot(roots[j].properties, hasPositionedAncestor = false) &&
                paintsAsLayer(roots[j])
        }
    }
    // Index-aligned with [roots], exactly as the Column walks them.
    return lifted.toList()
}

/** A px leaf that is ABSENT, or present and exactly 0 (`{px:0}` /
 *  `{original:{px:0}}`). A present-but-unresolvable leaf (`auto`, `em`, `%`)
 *  is NOT zero — the predicate below stays conservative on it. */
private fun zeroOrAbsent(root: IRComponent, type: String): Boolean {
    // Absent ⇒ the initial value (0 for the sizing/edge properties asked).
    val prop = root.properties.firstOrNull { it.type == type } ?: return true
    // Present: it must resolve to a concrete 0 px.
    return ValueExtractors.extractDp(prop.data)?.value == 0f
}

/** The block-axis edges that must be zero for a box to self-collapse
 *  (CSS 2.1 §8.3.1: "no top or bottom border, no top or bottom padding");
 *  physical AND the horizontal-tb logical spellings the wire may carry. */
private val SELF_COLLAPSE_ZERO_EDGES = listOf(
    "PaddingTop", "PaddingBottom", "PaddingBlockStart", "PaddingBlockEnd",
    "BorderTopWidth", "BorderBottomWidth", "BorderBlockStartWidth", "BorderBlockEndWidth",
)

/**
 * wave-52 lane L2 (T2-roots) — CSS 2.1 §8.3.1: "If the top and bottom margins
 * of a box are adjoining, then its margins collapse through it" — a box with
 * zero (or auto) computed height, no in-flow children, no line boxes, and no
 * block border or padding. The composed stack marked only RC1/hoisted roots
 * margin-transparent, so an EMPTY in-flow root (css-text-decor/
 * text-decoration-propagation-shadow's post-load `<p>` with `height: 0;
 * margin: 16px 0`) CLOSED the adjoining set and the natives emitted 16 + 16
 * where Chrome emits ONE 16-px gap (the underline sat 16 px too low on both
 * natives, wave51-fix ios f 0.9484 / android f 0.9486; web P 1.0000).
 *
 * Deliberately NARROW (the blast radius is the whole composed corpus):
 *  - not out-of-flow (§8.3.1's in-flow precondition; those roots have their
 *    own transparency rule), not the `body-root` (its margins are the
 *    canvas-margin resolver's — M1 — not a stacked box's);
 *  - no text, no runs, no marker, no generated content, no composed children;
 *  - `Display` absent or BLOCK — a table / flex / inline-level box is not a
 *    §8.3.1 self-collapsing block (gradient-hue-direction's baked `<hr>` is
 *    excluded one line later by its 1-px inset border anyway);
 *  - `Height` and `MinHeight` absent or exactly 0 px; every block padding
 *    and border width absent or exactly 0 px (has-style-sharing-002's
 *    `padding: 1em` root is NOT self-collapsing).
 * Transparency additionally requires the root's declared block margins to
 * be IN the fold (stripped) or absent — see [composedRootStackPlan] — so a
 * root that renders its own (unresolvable) margins keeps closing the set.
 */
fun isSelfCollapsingRoot(root: IRComponent): Boolean {
    // Out-of-flow boxes never take part in §8.3.1 (their transparency is the
    // hoist / static-position rule, not this one).
    if (isOutOfFlowRoot(root)) return false
    // The synthetic html+body bag is not a stacked block box.
    if (root.role == "body-root") return false
    // Any content ⇒ line boxes / in-flow children ⇒ not self-collapsing.
    if (!root._text.isNullOrEmpty()) return false
    if (!root.children.isNullOrEmpty()) return false
    if (!root.runs.isNullOrEmpty()) return false
    if (root.markerText != null) return false
    // Generated content (`::before` / `::after`) puts line boxes in the box —
    // 35 corpus roots match every other clause here and carry a pseudo
    // (counter-name-case-sensitive's `::before { content: "1-5" }` divs).
    // Conservative: ANY pseudo payload keeps the root opaque (the wave-51
    // behaviour), even an empty-string one §9.4.2 would let collapse.
    if (!root.pseudos.isNullOrEmpty()) return false
    // Only a block box (absent Display = block-level root in the composed stack).
    val display = (root.properties.firstOrNull { it.type == "Display" }?.data as? JsonPrimitive)
        ?.contentOrNull?.uppercase()
    if (display != null && display != "BLOCK") return false
    // Zero (or absent) height floor and used height.
    if (!zeroOrAbsent(root, "Height") || !zeroOrAbsent(root, "MinHeight")) return false
    // No block border, no block padding.
    return SELF_COLLAPSE_ZERO_EDGES.all { zeroOrAbsent(root, it) }
}

/**
 * wave-52 lane L2 — one composed root's stack plan, the pure body of what the
 * `rootPlans` lambda in ScreenshotCaptureScreen used to inline (wave 17 → 46
 * history in its kdoc there). Three shapes:
 *
 *  1. CANVAS-HOISTED root (fixed / inset absolute — the overlay): (0, 0),
 *     transparent, nothing stripped. §8.3.1 "margins of absolutely positioned
 *     boxes do not collapse"; §9.3.1 the box is absent from the flow. Unchanged.
 *  2. RC1 STATIC-POSITION root under an active host (absolute, NO inset) —
 *     wave-52 L2 T1: ALSO (0, 0), transparent, and NOT stripped. Through wave
 *     51 its own declared margins were joined into the collapse set and
 *     stripped from the box ("§8.3.1's empty-box model"), so
 *     css-masking/clip-path/clip-path-ellipse-006's `margin: 50px` abspos root
 *     landed at max(UA 16, 50) = 50 below the `<p>` where Chrome puts it at
 *     16 + 50 = 66 (16 px too high on both natives, wave51-fix f 0.9488 /
 *     0.9480; web 1.0000). §8.3.1: an abspos box's margins do not collapse
 *     — not even with the preceding sibling's bottom margin — and §10.6.4's
 *     static position is where the box's top MARGIN edge would be, so the
 *     preceding margin resolves in full ABOVE the slot and the box's own
 *     margin then offsets its ink from that slot. The slot anchor therefore
 *     takes the neighbours' collapsed gap alone, and the box keeps rendering
 *     its declared margins through MarginApplier (stripDeclared = false, no
 *     LocalCollapsedMargin override).
 *  3. IN-FLOW root: the wave-19/22/45/46 plan verbatim (UA + static declared
 *     edges through the SAME classifier the renderer paints with, em basis,
 *     hoist band folded in) — plus wave-52 L2 T2-roots: a self-collapsing
 *     empty root whose block margins are all in the fold (stripped, or none
 *     declared) is margin-TRANSPARENT, so the set stays open across it and
 *     its neighbours share ONE max() gap (§8.3.1 collapse-through). A root
 *     that must render its own margins (R4/R5 bail) stays opaque.
 *
 * @param hostActive whether CanvasRootHoist.Host will activate for this
 *   document (`CanvasRootHoist.hostActivates(roots)`) — the RC1 anchor is
 *   host-gated in the renderer, so shape 2 exists only under an active host.
 */
fun composedRootStackPlan(root: IRComponent, hostActive: Boolean): RootStackMargin {
    // Shape 1 — hoisted to the overlay: absent from the flow, own margins
    // render in the overlay and never push flow content.
    if (CanvasRootHoist.shouldHoistToCanvasRoot(root.properties, hasPositionedAncestor = false)) {
        return RootStackMargin(0f, 0f, stripDeclared = false, marginTransparent = true)
    }
    // Shape 2 — the RC1 static-position slot (wave-52 L2 T1): neighbours'
    // gap above the slot, own margins on the box, nothing joined or stripped.
    if (isComposedStaticPositionRoot(root, hostActive)) {
        return RootStackMargin(0f, 0f, stripDeclared = false, marginTransparent = true)
    }
    // Shape 3 — in-flow. Which block sides the IR declares (any Margin* covering them).
    val declared = declaredMarginSides(root.properties.map { it.type })
    // Static declared (top, bottom) px via the harness's em-aware classifier
    // (StaticEmMargin — a strict superset of the renderer's on non-em wires,
    // pin E8); null bails to R4/R5. A flow-sized out-of-flow root (host
    // inactive — the RC1 anchor never engages) bails too: it renders its own
    // margins in the flow, exactly the pre-wave-18 behaviour.
    val staticEdges = if (isOutOfFlowRoot(root)) null else StaticEmMargin.verticalEdges(root.properties)
    // wave-46 Y8: the UA default's em basis is the root's OWN computed
    // font-size; the tag gates the monospace fixed-default rung.
    val uaBasisPx = UaBlockMarginFontBasis.ownFontSizePx(root.properties, sourceTag = root._tag)
    // The R1–R5 plan (author > UA per edge, css-cascade-4 §6.1).
    val base = rootStackMargin(root._tag, "top" in declared, "bottom" in declared, staticEdges,
        ownFontSizePx = uaBasisPx)
    // wave-52 L2 T2-roots: collapse THROUGH an empty root whose block margins
    // all live in the fold — stripped (R2/R3) or never declared (R1, UA only).
    val blockMarginsInFold = base.stripDeclared || ("top" !in declared && "bottom" !in declared)
    val plan = base.copy(marginTransparent = isSelfCollapsingRoot(root) && blockMarginsInFold)
    // wave-26 (lane RES residual 3a): fold in the HOIST BAND the root's own
    // §8.3.1 plan would otherwise emit as padding outside its border box —
    // read through the runtime's own plan builder, so the number folded here
    // is exactly the number suppressed at the render (one decision, two
    // consumers). Transparent roots are returned unchanged by withHoistBand.
    val band = ComponentRenderer.composedRootHoistBand(root, uaBlockMargins = true)
    return withHoistBand(plan, band.topPx to band.bottomPx)
}

/**
 * wave-52 lane L2 (T6) — a CANVAS-HOISTED root (fixed, or absolute with an
 * inset) keeps its tag's UA block margin. CSS 2.1 §9.3.2 / §10.6.4: `top`
 * offsets the box's MARGIN edge, so an abspos `<p style="top:0">` paints its
 * border box 1em below the containing block's edge. The composed canvas
 * emits UA block margins ONLY through the root-stack fold (gap Spacers),
 * which hoisted roots never enter (shape 1 above, zero footprint), so such a
 * root rendered with NO margin: CSS2/css21-errata/s-11-1-1b-006's prose sat
 * 16 px high on both natives (wave51-fix ios f 0.9321 / android f 0.9332; web
 * P — the browser applies the UA sheet). The fix hands the overlay the UA
 * margin as two ordinary declared longhands, which the overlay already
 * renders like any author margin.
 *
 * Narrow by construction: identity unless the root hoists AND its tag has a
 * UA block margin AND it declares no block margin of its own (author > UA,
 * css-cascade-4 §6.1). Census over the 1435 wave51-fix docs: exactly ONE
 * carrier (006's root 3). Em basis = the root's own font-size (wave 46 Y8).
 * Twin: SwiftUI `UABlockMargin.withUaBlockMarginOnHoistedRoot`.
 */
fun withUaBlockMarginOnHoistedRoot(root: IRComponent): IRComponent {
    // In-flow and RC1 roots get their UA margin from the fold; only the
    // overlay's roots are this rule's.
    if (!CanvasRootHoist.shouldHoistToCanvasRoot(root.properties, hasPositionedAncestor = false)) return root
    // Any author block margin wins over the UA sheet — keep the root as declared.
    val declared = declaredMarginSides(root.properties.map { it.type })
    if ("top" in declared || "bottom" in declared) return root
    // The tag's UA block margin at the root's own em basis.
    val (top, bottom) = uaVerticalBlockMargins(
        root._tag, UaBlockMarginFontBasis.ownFontSizePx(root.properties, sourceTag = root._tag))
    // A tag with no UA block margin (div, span, …) is untouched.
    if (top == 0f && bottom == 0f) return root
    // Two absolute-px longhands in the wire's own leaf shape (`{"px": N}`).
    fun px(v: Float) = kotlinx.serialization.json.JsonObject(mapOf("px" to JsonPrimitive(v)))
    return root.copy(properties = root.properties + listOf(
        com.styleconverter.runtime.core.ir.IRProperty(type = "MarginTop", data = px(top)),
        com.styleconverter.runtime.core.ir.IRProperty(type = "MarginBottom", data = px(bottom)),
    ))
}
