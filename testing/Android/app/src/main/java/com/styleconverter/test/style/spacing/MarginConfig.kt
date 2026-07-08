package com.styleconverter.test.style.spacing

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

import com.styleconverter.test.style.core.types.LengthValue

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
        val startLogical = if (isRtl) inlineEnd else inlineStart
        val endLogical = if (isRtl) inlineStart else inlineEnd
        return Resolved(
            top = top ?: blockStart,
            right = right ?: endLogical,
            bottom = bottom ?: blockEnd,
            left = left ?: startLogical,
        )
    }

    data class Resolved(
        val top: MarginValue?,
        val right: MarginValue?,
        val bottom: MarginValue?,
        val left: MarginValue?,
    )
}
