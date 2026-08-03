package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent
// Wave 25 (lane UAM): the single Kotlin owner of the UA vertical table —
// shared with the runtime's block-CHILD fold so root and descendant agree.
import com.styleconverter.runtime.spacing.uaVerticalBlockMargins

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

/** UA default block margins in px (at a 16px root font). */
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
fun uaBlockMargins(sourceTag: String?): UaMargins {
    // Wave 25 (lane UAM): the VERTICAL half is no longer duplicated here.
    // The runtime owns the ONE Kotlin copy of the table (spacing/
    // UaBlockChildMargins.uaVerticalBlockMargins) because the block-CHILD
    // fold in ComponentRenderer needs it too — a root and a descendant
    // must never disagree about what a `<p>` margin is worth. Values are
    // unchanged (p 16 · h1 21 · h2 19 · h3 16 · h4 21 · h5 27 · h6 37 ·
    // ul/ol/blockquote/pre/figure 16 · everything else 0), so every
    // Round-4 / RC-A4 pin in UaBlockMarginsTest keeps its number.
    val (top, bottom) = uaVerticalBlockMargins(sourceTag)
    // The HORIZONTAL half stays local: only blockquote and figure carry a
    // UA inline inset (`margin: 1em 40px`), and §8.3.1 collapses the block
    // axis only — so the child fold has no use for these and the runtime
    // table deliberately omits them.
    val inline = when (sourceTag?.lowercase()) {
        "blockquote", "figure" -> 40
        else -> 0
    }
    return UaMargins(top.toInt(), bottom.toInt(), inline, inline)
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
fun effectiveUaMargins(sourceTag: String?, declaredSides: Set<String>): UaMargins {
    val ua = uaBlockMargins(sourceTag)
    return UaMargins(
        top = if ("top" in declaredSides) 0 else ua.top,
        bottom = if ("bottom" in declaredSides) 0 else ua.bottom,
        left = if ("left" in declaredSides) 0 else ua.left,
        right = if ("right" in declaredSides) 0 else ua.right,
    )
}

/** [effectiveUaMargins] for a decoded component (reads `_tag` + property types). */
fun effectiveUaMargins(component: IRComponent): UaMargins =
    effectiveUaMargins(component._tag, declaredMarginSides(component.properties.map { it.type }))

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
 */
fun rootStackMargin(
    sourceTag: String?,
    declaresTop: Boolean,
    declaresBottom: Boolean,
    staticDeclaredPx: Pair<Float, Float>?,
): RootStackMargin {
    // UA defaults for this tag (per-side deferral handled below).
    val ua = uaBlockMargins(sourceTag)
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
