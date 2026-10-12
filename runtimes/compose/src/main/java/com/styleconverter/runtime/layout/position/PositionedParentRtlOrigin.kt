package com.styleconverter.runtime.layout.position

// Wave 55 (lane L3, U2) — the PHYSICAL-LEFT origin of an RTL parent's
// positioned-parent flow slot (PositionedParentFlowSlot.mount).
//
// ## The measured defect (wave54-final, android)
// css-writing-modes/dynamic-offset-rtl-001: a 100×100 `position: absolute;
// direction: rtl` box holds a 10×10 `left: 70px` child. Android painted the
// child at x186 = 16 + 100 + 70, OUTSIDE its parent (ref x86). The slot's
// zero-flow anchor reports 0×0, and the parent's Column — laid out RTL
// because ComponentRenderer provides LocalLayoutDirection Rtl for a
// `Direction: RTL` box — aligns a 0-wide child at `Alignment.Start` =
// `space − size` = the Column's full width W. The child's own PositionApplier
// offset (`absoluteOffset`, physical) then adds `left` from THAT edge.
// Unmounted (a host-inactive capture) the same Column puts a w-wide child at
// W − w + left: bidi-tab-001's four RTL rows and dir-style-02a rows 6/9 are
// mirrored (dir-style-02a ink x246-373 where the ref has x17-153).
//
// ## What the spec says
// CSS 2.1 §10.3.7 and css-position-3 §3.1: a declared `left` (with `right`
// auto) is the offset from the containing block's LEFT edge whatever its
// `direction`; direction only picks which inset an all-auto or
// over-constrained box resolves (css-writing-modes-4 §2.1: `direction` sets
// the inline base direction, not the physical meaning of `left`). The
// containing block is the parent (§10.1 item 4), so the ink belongs at
// `left` — and a `right`-only box at `W − w − right`.
//
// ## The fix — one outer layout element
// An outer `Modifier.layout` places the zero-flow anchor at
// x = −constraints.maxWidth. For a parent with a px Width the Column's
// constraints are exact, so its width W equals the maxWidth it hands the
// child, and the ink lands at W − maxWidth + left = left. The constraints
// pass through untouched, so zeroFlowAnchor's §10.3.7 band and its
// slot-relative `right` anchor (maxWidth − w) still read the Column's width:
// a `right`-only child lands at W − w − right, physical right. `place`, never
// `placeRelative`: the translation is physical (placeRelative under Rtl
// would mirror it to +maxWidth).
//
// ## What it deliberately does NOT do (declined with a breadcrumb)
//  * a parent without a px Width (auto / % / calc): W need not equal the
//    child's maxWidth (risk R1 of the lane brief) — today's placement stays;
//  * a child with no readable physical inline inset (both auto, a
//    logical-only `inset-inline-*`, or a calc()): PositionConfig folds the
//    logical insets as LTR (its class kdoc), so no origin is honest here;
//  * a child with BOTH `left` and `right`: §10.3.7's RTL over-constraint
//    rule (ignore `left`) is not modelled by this slot.
// The wave-54 corpus has none of the three among its 11 RTL Column-loop
// parents / 13 children (all declare a px Width and only `left`).

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors

object PositionedParentRtlOrigin {

    /** Breadcrumb: the RTL parent's width is not a px length (R1). */
    const val PARENT_WIDTH_BREADCRUMB = "PositionedParentSlot[rtl-origin-declined:parent-width-not-px]"

    /** Breadcrumb: the child resolves no physical inline inset. */
    const val NO_INSET_BREADCRUMB = "PositionedParentSlot[rtl-origin-declined:no-physical-inline-inset]"

    /** Breadcrumb: the child declares both `left` and `right`. */
    const val BOTH_INSETS_BREADCRUMB = "PositionedParentSlot[rtl-origin-declined:both-inline-insets]"

    /** Why the origin does not apply, with the breadcrumb each reason records. */
    enum class RtlOriginDecline(val breadcrumb: String?) {
        /** It applies. */
        NONE(null),

        /** An LTR parent: its Column already starts at the left edge. */
        NOT_RTL(null),

        /** See [PARENT_WIDTH_BREADCRUMB]. */
        PARENT_WIDTH_NOT_PX(PARENT_WIDTH_BREADCRUMB),

        /** See [NO_INSET_BREADCRUMB]. */
        NO_PHYSICAL_INLINE_INSET(NO_INSET_BREADCRUMB),

        /** See [BOTH_INSETS_BREADCRUMB]. */
        BOTH_INLINE_INSETS(BOTH_INSETS_BREADCRUMB),
    }

    /**
     * Does the parent declare a px width? The last `Width` / `InlineSize`
     * declaration wins (the extractors' fold order); a px length is what
     * [ValueExtractors.extractDp] reads, a `%` or `calc()` is not.
     */
    internal fun hasPxWidth(parentProperties: List<IRProperty>): Boolean =
        parentProperties.lastOrNull { it.type == "Width" || it.type == "InlineSize" }
            ?.let { ValueExtractors.extractDp(it.data) != null } == true

    /** The decision, pure over the IR (pinned in PositionedParentRtlOriginTest). */
    fun decline(
        parentRtl: Boolean,
        parentProperties: List<IRProperty>,
        childProperties: List<IRProperty>,
    ): RtlOriginDecline {
        // Only an RTL Column mis-places the 0-wide slot.
        if (!parentRtl) return RtlOriginDecline.NOT_RTL
        // W == maxWidth needs a definite parent width (R1).
        if (!hasPxWidth(parentProperties)) return RtlOriginDecline.PARENT_WIDTH_NOT_PX
        // The child's PHYSICAL inline insets, read by the SAME extractor its
        // own PositionApplier offset reads (`Left` → start, `Right` → end).
        val config = PositionExtractor.extractPositionConfig(childProperties.map { it.type to it.data })
        val left = config.start != null
        val right = config.end != null
        return when {
            // Over-constrained under RTL: not modelled (header).
            left && right -> RtlOriginDecline.BOTH_INLINE_INSETS
            // Exactly one physical inset: the origin makes it physical.
            left || right -> RtlOriginDecline.NONE
            // Nothing physical to anchor (header).
            else -> RtlOriginDecline.NO_PHYSICAL_INLINE_INSET
        }
    }

    /** True when [decline] answers [RtlOriginDecline.NONE]. */
    fun applies(
        parentRtl: Boolean,
        parentProperties: List<IRProperty>,
        childProperties: List<IRProperty>,
    ): Boolean = decline(parentRtl, parentProperties, childProperties) == RtlOriginDecline.NONE

    /** The origin for a slot whose bounded max width is [boundedMaxPx]: −maxWidth, or null when unbounded. */
    fun originPx(boundedMaxPx: Int?): Int? = boundedMaxPx?.let { -it }

    /** Where the outer element PLACES its content, in px: the origin, physical (`place`), or 0 with none. */
    internal fun placedXPx(originPx: Int?): Int = originPx ?: 0

    /**
     * The pure model of where a mounted child's ink starts, relative to the
     * Column's left edge: the Column's start alignment of the 0×0 mount
     * (`colWidth` under Rtl, 0 under Ltr), the origin's placement, the
     * anchor's slot-relative `right` edge (CanvasRootHoist.anchorPlacePx, the
     * real arithmetic) and the child's own physical offset (+left / −right).
     */
    fun inkLeftPx(
        colWidthPx: Int,
        maxWidthPx: Int,
        rtl: Boolean,
        origin: Boolean,
        leftPx: Float? = null,
        rightPx: Float? = null,
        boxWidthPx: Int = 0,
    ): Float {
        // Alignment.Start of a 0-wide child: `space − size` under Rtl.
        val slotX = if (rtl) colWidthPx else 0
        // The outer element's physical translation (none without it).
        val originX = if (origin) placedXPx(originPx(maxWidthPx)) else 0
        // zeroFlowAnchor: a `right`-only box anchors at maxWidth − box (A5).
        val anchorX = CanvasRootHoist.anchorPlacePx(
            originPx = 0,
            endEdgePx = PositionedParentFlowSlot.slotEndEdgePx(leftPx == null && rightPx != null, maxWidthPx),
            boxPx = boxWidthPx,
        )
        // PositionConfig.offsetX: `left` wins, else −right, else 0.
        val offsetX = leftPx ?: -(rightPx ?: 0f)
        return slotX + originX + anchorX + offsetX
    }

    /**
     * The outer layout element: measure the zero-flow anchor with the
     * Column's own constraints (untouched — see the header), report its size
     * (0×0) and place it at [originPx] of the bounded max width.
     */
    fun originModifier(): Modifier = Modifier.layout { measurable, constraints ->
        // Pass the Column's constraints through: the anchor reads them.
        val placeable = measurable.measure(constraints)
        // −maxWidth when bounded; an unbounded slot keeps today's origin.
        val origin = originPx(if (constraints.hasBoundedWidth) constraints.maxWidth else null)
        // Transparent in size: the mount still occupies no flow space.
        layout(placeable.width, placeable.height) {
            // Physical, never mirrored (header).
            placeable.place(x = placedXPx(origin), y = 0)
        }
    }
}
