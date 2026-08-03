package com.styleconverter.runtime.spacing

// BlockMarginCollapse — CSS2 §8.3.1 "Collapsing margins", scoped emulation
// for the Compose BLOCK path (ComponentRenderer's default block child loop).
//
// Why: Compose has no margin primitive, so MarginApplier bakes each child's
// margin as OUTER absolutePadding on that child. In a spacing-0 Column two
// adjoining 20px sibling margins therefore STACK to 40px where the browser
// collapses them to max(20,20)=20 (CSS2 §8.3.1: "adjoining vertical margins
// collapse"). Worse, a first/last child's block-axis edge margin stayed
// INSIDE the parent's border box while the browser lets it collapse THROUGH
// a padding-0 / border-0 parent and escape (§8.3.1: parent/first-child top
// margins are adjoining when "the element has no top border and no top
// padding"; ditto bottom for height:auto parents). Measured on the
// spacing/MarginTrim fixture: natives drew the parent 240px tall with bars
// inset 20px, web drew a 300x160 parent at canvas y=36 with flush bars
// (pair SSIM 0.832).
//
// Mechanism: the block branch computes a per-container "plan" — collapsed
// applied margins per child, plus the hoisted (escaped) edge amounts — and
// hands each child its override through [LocalCollapsedMargin]; the child's
// MarginApplier substitutes the block-axis sides before building modifiers
// (the CompositionLocal mechanism MarginTrimApplier's file comment sketched).
// The hoisted amounts become TRANSPARENT outer padding on the parent wrapper
// so the parent background does not paint the escaped region — exactly the
// browser's geometry for collapse-through.
//
// Scope (deliberate, matching the committed fixture corpus): positive
// absolute-px margins in the default block flow only. Auto / negative /
// relative margins, out-of-flow or floated children, and non-block parents
// all fall back to the existing stacking behavior — the caller logs the
// fallback once (house rule: no silent fallthroughs). The full eligibility
// and gate rules are the UNIFIED COLLAPSE GATE CONTRACT (bails B1-B10,
// gates G1-G5) implemented byte-for-byte identically by this file +
// ComponentRenderer.blockCollapsePlanFor on Android and their SwiftUI
// counterparts — the shared S1-S12 pin table in the two platforms' test
// suites asserts IDENTICAL expected values so the implementations cannot
// drift apart silently.

import androidx.compose.runtime.compositionLocalOf
import com.styleconverter.runtime.core.types.LengthValue
import kotlinx.serialization.json.JsonElement

/**
 * The APPLIED block-axis margins for one in-flow block child after §8.3.1
 * collapsing: what MarginApplier should actually bake as outer spacing.
 * Inline-axis (left/right) margins never collapse (§8.3.1 is vertical-only)
 * and are untouched by this override.
 */
data class CollapsedMargin(
    // Applied top margin in px (post-collapse; 0 when hoisted to the parent).
    val topPx: Float,
    // Applied bottom margin in px (0 for every child except a non-hoisted last).
    val bottomPx: Float,
)

/**
 * A whole container's collapse resolution: hoisted edge spacing for the
 * parent wrapper plus one [CollapsedMargin] per child (index-aligned with
 * the component's children list).
 */
data class BlockCollapsePlan(
    // Extra transparent spacing ABOVE the parent's border box — the part of
    // the first child's top margin that escapes through the parent edge
    // beyond the parent's own top margin (§8.3.1 collapse-through).
    val hoistTopPx: Float,
    // Same for the last child's bottom margin below the parent border box.
    val hoistBottomPx: Float,
    // Applied block-axis margins per child, in children order.
    val perChild: List<CollapsedMargin>,
)

object BlockMarginCollapse {

    /**
     * Per-child margin override channel. The parent's block loop provides a
     * [CollapsedMargin] around each child's RenderComponent; the child reads
     * it and passes it into StyleApplier → MarginApplier. Default null =
     * "not inside a collapsing block container" — every other render path
     * (flex/grid/table/standalone captures) keeps byte-identical behavior.
     */
    val LocalCollapsedMargin = compositionLocalOf<CollapsedMargin?> { null }

    /**
     * Wave 26 (lane RES residual 3a) — HOIST-BAND suppression channel for the
     * composed capture's ROOT slots.
     *
     * ## The double count this closes
     * A composed root's block-edge spacing has TWO owners: the harness's root
     * -stack fold emits it as a Spacer between roots (collapsedRootStackGapsPx),
     * and this container's own plan emits `hoistTopPx/hoistBottomPx` as
     * transparent padding outside the root's border box. Both are transparent
     * outer spacing in the SAME adjoining-margin region, so they ADD where
     * CSS 2.1 §8.3.1 takes ONE max() over the whole chain. Concretely, with a
     * previous root ending in a 40px bottom margin, a `<blockquote>` root
     * (UA 16) whose first child is an `<h5>` (UA 27): the fold emits
     * max(40, 16) = 40 and the band adds max(16, 27) − 16 = 11 → 51px, where
     * the browser gives max(40, 16, 27) = 40.
     *
     * ## Why suppression and not subtraction
     * Subtracting the band from the gap cannot work: two adjacent band-
     * carrying roots (A's last child bottom 16, B's first child top 16) need
     * gap = 16 − 16 − 16 = −16, which floors at 0 and renders 32. The only
     * model that reproduces the ref is ONE owner — so the root-stack fold
     * takes the root's COLLAPSED-THROUGH edges (own margin max'd with the
     * hoisted child edge, via [composedRootHoistBand]) and this local tells
     * the root's own container branch to emit NO band.
     *
     * ## Why it carries an ID, not a Boolean
     * Scope must be exactly ONE level — a nested container still emits its
     * own band. A Boolean flag would need an explicit reset on every path
     * that renders children (block, flex, grid, marker rows, overlays), and
     * ONE missed reset silently deletes a descendant's band. Keyed on the
     * flagged component's `id` instead, the scope is leak-proof by
     * construction and needs no reset: the extractor's ids are hierarchical
     * (`<root>__<i>`, a child extending its parent's id — tools/titan/
     * extract-fixture.mjs), so a descendant's id can never EQUAL its
     * ancestor's. Default null ⇒ nothing is ever suppressed on any
     * non-composed path, so the 327 dark-stage baselines are byte-identical.
     * The id is also what the SwiftUI twin can key on (IRComponent is a
     * value type there), which keeps the two pin tables literally identical.
     */
    val LocalHoistBandSuppressedFor = compositionLocalOf<String?> { null }

    /**
     * Pure predicate for the channel above — true iff [componentId] is
     * exactly the id the host flagged. Byte-parallel twin of Swift's
     * `MarginCollapse.suppressesHoistBand`.
     */
    fun suppressesHoistBand(suppressedForId: String?, componentId: String): Boolean =
        suppressedForId != null && suppressedForId == componentId

    /** Hoist eligibility per parent edge (see [hoistGates]). */
    data class HoistGates(val top: Boolean, val bottom: Boolean)

    /**
     * Read a margin config's block-axis sides as plain non-negative px, or
     * null when the config uses value flavors outside this emulation's scope
     * (auto / negative / relative / calc) — the caller must then fall back
     * to the current stacking behavior for the WHOLE container (collapsing
     * only some siblings would produce geometry neither engine renders).
     *
     * Unset sides count as 0 (CSS initial margin is 0, §8.3.1 treats a
     * missing margin as a zero-width adjoining margin).
     */
    fun blockMarginsOrNull(config: MarginConfig): CollapsedMargin? {
        // Resolve logical→physical first (margin-block-start feeds top in
        // horizontal-tb; isRtl only affects the inline axis we don't touch).
        val r = config.resolve(isRtl = false)
        // Each side must independently reduce to a plain px count.
        val top = plainPxOrNull(r.top) ?: return null
        val bottom = plainPxOrNull(r.bottom) ?: return null
        return CollapsedMargin(top, bottom)
    }

    /** One side → px when it is absent or an exact non-negative length. */
    private fun plainPxOrNull(v: MarginValue?): Float? = when (v) {
        // Unset side — the CSS initial value 0 (a zero adjoining margin).
        null -> 0f
        // margin: auto in the block axis computes to 0 in block flow (CSS2
        // §10.6.3) BUT our MarginApplier turns vertical auto pairs into
        // Compose centering — bail out so that path stays untouched.
        MarginValue.Auto -> null
        is MarginValue.Length -> when (val len = v.value) {
            // Exact px — in scope only when non-negative: §8.3.1's negative-
            // margin rules (max positive + min negative) are NOT emulated,
            // per the lane scope (committed fixtures are positive-only).
            is LengthValue.Exact ->
                if (len.px >= 0.0) len.px.toFloat() else null
            // Relative (%/em/vw) and calc need the live resolution context a
            // pure plan can't see; auto/intrinsic/fraction are not margins.
            else -> null
        }
    }

    /**
     * The §8.3.1 collapse math, pure for JVM pinning:
     *  - between siblings, child i's applied top = max(prev bottom, own top)
     *    ("the maximum of the adjoining margin widths"), with the previous
     *    child's bottom applied as 0 so the gap is carried exactly once;
     *  - the first child's top / last child's bottom margin HOIST to the
     *    parent wrapper when the corresponding gate allows collapse-through,
     *    contributing max(parent own margin, child edge margin) minus the
     *    parent's own margin (already applied by the parent's MarginApplier)
     *    as extra transparent outer spacing;
     *  - when a gate is closed (padding/border/height/overflow separate the
     *    margins) the edge child keeps its full margin INSIDE the parent —
     *    identical to the pre-collapse behavior for that edge.
     *
     * [parentOwn] is the parent's own plain block-axis margins (needed for
     * the max() composition above); callers pass (0,0) when the parent has
     * no margin of its own.
     */
    fun computePlan(
        children: List<CollapsedMargin>,
        parentOwn: CollapsedMargin,
        gates: HoistGates,
    ): BlockCollapsePlan {
        // Applied margins per child, per the rules documented above.
        val applied = children.mapIndexed { i, m ->
            val top = when {
                // First child: hoisted edges apply 0 inside the parent (the
                // margin re-appears as wrapper spacing); closed gate keeps it.
                i == 0 -> if (gates.top) 0f else m.topPx
                // Interior sibling: §8.3.1 max() of the two adjoining margins.
                else -> maxOf(children[i - 1].bottomPx, m.topPx)
            }
            // Bottom margins apply 0 everywhere except a non-hoisted last
            // child: interior bottoms are carried by the NEXT child's max().
            val bottom =
                if (i == children.lastIndex && !gates.bottom) m.bottomPx else 0f
            CollapsedMargin(top, bottom)
        }
        // Hoisted amounts: the collapsed parent-edge margin is
        // max(parent own, child edge); the parent's own share is already
        // rendered by its MarginApplier, so only the excess is added.
        val hoistTop =
            if (gates.top && children.isNotEmpty())
                (maxOf(parentOwn.topPx, children.first().topPx) - parentOwn.topPx)
            else 0f
        val hoistBottom =
            if (gates.bottom && children.isNotEmpty())
                (maxOf(parentOwn.bottomPx, children.last().bottomPx) - parentOwn.bottomPx)
            else 0f
        return BlockCollapsePlan(hoistTop, hoistBottom, applied)
    }

    /**
     * Substitute a child's block-axis margins with the plan's collapsed
     * values. Inline sides (left/right/inline-*) pass through untouched —
     * §8.3.1 only collapses vertical margins. blockStart/blockEnd are
     * cleared because MarginConfig.resolve() falls back to them only when
     * top/bottom are null, and the override always sets both.
     */
    fun applyOverride(config: MarginConfig, collapsed: CollapsedMargin): MarginConfig =
        config.copy(
            // Applied top: an exact px length (possibly 0 for hoisted edges).
            top = MarginValue.Length(LengthValue.Exact(collapsed.topPx.toDouble())),
            // Applied bottom: exact px (0 except a non-hoisted last child).
            bottom = MarginValue.Length(LengthValue.Exact(collapsed.bottomPx.toDouble())),
            // Logical block sides are subsumed by the physical override.
            blockStart = null,
            blockEnd = null,
        )

    /**
     * Decide per parent edge whether child margins may collapse THROUGH the
     * parent (§8.3.1's adjoining conditions), from the parent's property
     * pairs. Conservative: any condition we cannot prove keeps the gate
     * closed, which preserves the existing keep-inside behavior for that
     * edge (never new wrongness, only unfixed cases).
     */
    fun hoistGates(pairs: List<Pair<String, JsonElement?>>): HoistGates {
        // §8.3.1: parent/child top margins are adjoining only with "no top
        // border and no top padding" — resolve the parent's padding config
        // and require the block-axis sides to be provably zero (the live
        // converter emits explicit {"px":0.0} for `padding: 0`, so a
        // value-based check is required — presence alone would always block).
        val padding = PaddingExtractor.extract(pairs).resolve(isRtl = false)
        val padTopZero = isZeroOrAbsent(padding.top)
        val padBottomZero = isZeroOrAbsent(padding.bottom)
        // Border check is VALUE-BASED (unified gate contract G2): only a
        // border edge with a positive USED width separates the margins.
        // `border-top-width: 0` (or `border: 0`) leaves the gate OPEN —
        // CSS2 §8.3.1's condition is "no top border", i.e. used width 0.
        // Resolution is delegated to BorderSideExtractor so the gate reads
        // the SAME widths the runtime paints (shorthand BorderWidth/Style
        // merge, thin/medium/thick keywords, and the css-backgrounds-3
        // §4.3 `medium`=3px default for a visible style declared WITHOUT a
        // width — which therefore closes the gate). A style of none/hidden
        // without a width keeps used width 0 → gate stays open.
        val border = BorderGateBridge.edgeBands(pairs)
        // css-overflow-3 §2.1 / CSS2 §9.4.1: non-visible overflow makes the
        // parent a block formatting context root, and "margins of elements
        // that establish new block formatting contexts do not collapse with
        // their in-flow children" — close both gates. EITHER axis counts:
        // per css-overflow-3 §2.1 `overflow-x: hidden` computes overflow-y
        // to auto, so one non-visible axis already implies a BFC (G3).
        val overflow = OverflowExtractorBridge.isScrollContainer(pairs)
        // Unified gate contract G4: an absolutely/fixed-positioned parent is
        // out of flow and establishes a new BFC-like context (CSS2 §9.4.1 /
        // §10.1) — its margins never collapse with its children's. Close
        // BOTH gates but keep interior sibling collapse (pin S7).
        val outOfFlowParent = pairs.firstOrNull { (type, _) -> type == "Position" }
            ?.second
            ?.let { com.styleconverter.runtime.core.types.ValueExtractors.extractKeyword(it)?.uppercase() }
            .let { it == "ABSOLUTE" || it == "FIXED" }
        // §8.3.1: the parent/last-child BOTTOM margins are adjoining only
        // when the parent's height is auto ("height is auto and min-height
        // is zero") — any explicit block-size, min-block-size, or
        // aspect-ratio (which derives a definite height; css-sizing-4 §5)
        // pins the bottom edge away from the child margin. MaxHeight /
        // MaxBlockSize are deliberately NOT in the set (contract G5):
        // max-height leaves the used height auto-derived, so collapse-
        // through still happens (CSS2 §8.3.1 names only height/min-height).
        // Presence-based: an explicit `height: auto` wire value is not
        // emitted by the converter for these fixtures.
        val heightPinned = pairs.any { (type, _) -> type in HEIGHT_PINNING_TYPES }
        return HoistGates(
            top = padTopZero && !border.top && !overflow && !outOfFlowParent,
            bottom = padBottomZero && !border.bottom && !overflow &&
                !outOfFlowParent && !heightPinned,
        )
    }

    /** Padding side provably zero: absent, or an exact 0 length. */
    private fun isZeroOrAbsent(v: LengthValue?): Boolean =
        v == null || (v is LengthValue.Exact && v.px == 0.0)

    // Block-size-pinning types for the bottom gate (see hoistGates comment;
    // MaxHeight/MaxBlockSize deliberately absent per contract G5).
    private val HEIGHT_PINNING_TYPES = setOf(
        "Height", "BlockSize", "MinHeight", "MinBlockSize", "AspectRatio",
    )

    /**
     * Value-based border bands per block-axis edge (contract G2), resolved
     * through BorderSideExtractor — the single owner of border-width
     * parsing — so the gate agrees byte-for-byte with what the renderer
     * actually paints. `true` = a band separates the margins (gate closes).
     */
    private object BorderGateBridge {
        /** Border-band presence for the top/bottom edges. */
        data class EdgeBands(val top: Boolean, val bottom: Boolean)

        fun edgeBands(pairs: List<Pair<String, JsonElement?>>): EdgeBands {
            // css-backgrounds-3 §6: border-image paints INSTEAD of the
            // border styles but still occupies the border area (and can
            // even extend it via outset) — any BorderImage* declaration
            // conservatively closes both gates (contract G2: "BorderImage
            // closes"). Radius types never reach this check: the extractor
            // below ignores them entirely (corner rounding adds no band).
            if (pairs.any { (type, _) -> type.startsWith("BorderImage") }) {
                return EdgeBands(top = true, bottom = true)
            }
            // Resolve declared widths exactly as the paint path does:
            // shorthand merge + thin/medium/thick keywords + the medium=3px
            // default for visible styles without a width (§4.3).
            val resolved = com.styleconverter.runtime.borders.sides.BorderSideExtractor
                .extractBorderConfig(pairs)
            return EdgeBands(
                top = bandOn(resolved.top, widthDeclared(pairs, TOP_WIDTH_TYPES)),
                bottom = bandOn(resolved.bottom, widthDeclared(pairs, BOTTOM_WIDTH_TYPES)),
            )
        }

        /** One edge: does a border band separate the adjoining margins? */
        private fun bandOn(
            side: com.styleconverter.runtime.borders.sides.BorderSideConfig,
            widthDeclared: Boolean,
        ): Boolean = when {
            // A resolved width decides directly: positive px closes the
            // gate, an explicit 0 keeps it OPEN (value-based, G2 —
            // `border: 0` still collapses per CSS2 §8.3.1).
            side.width != null -> side.width.value > 0f
            // Width declared but unresolvable to px here (calc/relative
            // flavors extractBorderWidth returns null for) — conservative:
            // we cannot PROVE zero, so keep the pre-collapse behavior for
            // this edge (never new wrongness, only unfixed cases).
            widthDeclared -> true
            // No width and no visible style (the extractor's medium default
            // already upgraded visible-style-without-width to 3px): used
            // border-width is 0 — no band, gate open.
            else -> false
        }

        /** Any width-carrying declaration for the edge present at all? */
        private fun widthDeclared(
            pairs: List<Pair<String, JsonElement?>>,
            edgeTypes: Set<String>,
        ): Boolean = pairs.any { (type, _) -> type in edgeTypes }

        // Width-family types feeding the TOP edge: the physical longhand,
        // the logical block-start alias (horizontal-tb), and the all-side
        // BorderWidth shorthand the wire can carry.
        private val TOP_WIDTH_TYPES =
            setOf("BorderTopWidth", "BorderBlockStartWidth", "BorderWidth")

        // Same for the BOTTOM edge (logical block-end maps to bottom).
        private val BOTTOM_WIDTH_TYPES =
            setOf("BorderBottomWidth", "BorderBlockEndWidth", "BorderWidth")
    }

    /**
     * Tiny indirection over OverflowExtractor so the gate reads the SAME
     * keyword parsing the runtime uses to build scroll containers (keeps
     * one source of truth for what counts as non-visible overflow).
     */
    private object OverflowExtractorBridge {
        fun isScrollContainer(pairs: List<Pair<String, JsonElement?>>): Boolean {
            // Reuse the scrolling extractor's full keyword table — hidden /
            // scroll / auto / clip on either axis establishes a BFC.
            val cfg = com.styleconverter.runtime.scrolling.OverflowExtractor
                .extractOverflowConfig(pairs)
            return cfg.overflowX != com.styleconverter.runtime.scrolling.OverflowBehavior.VISIBLE ||
                cfg.overflowY != com.styleconverter.runtime.scrolling.OverflowBehavior.VISIBLE
        }
    }
}
