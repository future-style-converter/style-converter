package com.styleconverter.runtime.spacing

// MarginConfig — like PaddingConfig, but each side is a MarginValue so we can
// distinguish the `auto` keyword from an unspecified side AND from a zero
// length. margin: auto centers the element along its axis; margin: 0 does
// not. Both are valid IR outputs and the Applier needs to tell them apart.
//
// IR shapes reaching this config (docs in Phase 2 spec):
//   {"px": 20}     → MarginValue.Length(Exact)
//   {"px": -10}    → MarginValue.Length(Exact) with negative value (legal)
//   "auto"         → MarginValue.Auto
//   10.0           → MarginValue.Length(Relative PERCENT)
//   mixed auto/0:  each longhand is independent, e.g. `margin: auto 0` emits
//                  MarginLeft="auto", MarginTop={px:0}, MarginRight="auto",
//                  MarginBottom={px:0}

import com.styleconverter.runtime.core.types.LengthValue

/**
 * A side of a margin: either a length (possibly relative), the `auto`
 * keyword, or unspecified (null at the Config level).
 */
sealed interface MarginValue {
    /** A real length. Negative values are allowed for margins. */
    data class Length(val value: LengthValue) : MarginValue

    /** The `auto` keyword — axis-aware centering by default. */
    data object Auto : MarginValue
}

/**
 * All 8 CSS margin longhands.
 */
data class MarginConfig(
    val top: MarginValue? = null,
    val right: MarginValue? = null,
    val bottom: MarginValue? = null,
    val left: MarginValue? = null,
    val blockStart: MarginValue? = null,
    val blockEnd: MarginValue? = null,
    val inlineStart: MarginValue? = null,
    val inlineEnd: MarginValue? = null,
    // Wave-47 lane Z2 — the css-writing-modes-4 §6.4 side mapping for THIS
    // component's used writing mode, stored by MarginExtractor and null for
    // every horizontal mode (see LogicalSides.verticalOrNull's blast-radius
    // contract: null keeps resolve() byte-identical to the legacy path).
    val logicalSides: LogicalSides? = null,
) {
    /** True if any side was specified. */
    val hasMargin: Boolean
        get() = top != null || right != null || bottom != null || left != null ||
            blockStart != null || blockEnd != null ||
            inlineStart != null || inlineEnd != null

    /** Shortcut — is any side Auto (horizontally)? Drives centering. */
    val hasHorizontalAuto: Boolean
        get() = left == MarginValue.Auto || right == MarginValue.Auto ||
            inlineStart == MarginValue.Auto || inlineEnd == MarginValue.Auto

    /** Shortcut — is any side Auto (vertically)? */
    val hasVerticalAuto: Boolean
        get() = top == MarginValue.Auto || bottom == MarginValue.Auto ||
            blockStart == MarginValue.Auto || blockEnd == MarginValue.Auto

    /** Resolve logical→physical the same way PaddingConfig does. */
    fun resolve(isRtl: Boolean): Resolved {
        // Wave-47 lane Z2 — a stored VERTICAL mapping wins: under
        // vertical-rl `margin-block-end` is a LEFT margin (css-writing-modes-4
        // §6.4), which the legacy lines below would transpose to bottom.
        // logicalSides is null for every horizontal mode (extractor contract),
        // so all pre-existing content takes the legacy path byte-identically.
        logicalSides?.let { return resolveWith(it) }
        val startLogical = if (isRtl) inlineEnd else inlineStart
        val endLogical = if (isRtl) inlineStart else inlineEnd
        return Resolved(
            top = top ?: blockStart,
            right = right ?: endLogical,
            bottom = bottom ?: blockEnd,
            left = left ?: startLogical,
        )
    }

    /**
     * The table-driven resolve: each physical side takes its declared
     * physical value first (CSS cascade — physical vs logical is a plain
     * cascade tie the extractor already ordered), then whichever logical
     * side §6.4 maps onto it. The mapping is a bijection, so exactly one
     * logical slot can feed each physical side.
     */
    private fun resolveWith(sides: LogicalSides): Resolved {
        // Which logical value lands on a given physical side under [sides].
        fun logicalFor(side: PhysicalSide): MarginValue? = when (side) {
            sides.blockStart -> blockStart
            sides.blockEnd -> blockEnd
            sides.inlineStart -> inlineStart
            else -> inlineEnd
        }
        return Resolved(
            top = top ?: logicalFor(PhysicalSide.TOP),
            right = right ?: logicalFor(PhysicalSide.RIGHT),
            bottom = bottom ?: logicalFor(PhysicalSide.BOTTOM),
            left = left ?: logicalFor(PhysicalSide.LEFT),
        )
    }

    data class Resolved(
        val top: MarginValue?,
        val right: MarginValue?,
        val bottom: MarginValue?,
        val left: MarginValue?,
    )
}
