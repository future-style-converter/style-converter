package com.styleconverter.runtime.spacing

// PaddingConfig — Phase 2 migration of the padding surface to the canonical
// Config/Extractor/Applier triplet. Each side is stored as a LengthValue? so
// percent/em/vw/calc variants survive to the Applier.
//
// Fixture coverage (examples/properties/spacing/):
//   padding-absolute.json  — px/pt/cm/mm/in/pc (all absolute → Exact)
//   padding-units.json     — em/rem/%/vw/vh/calc  (→ Relative or Calc)
//
// IR shapes reaching this config (docs in Phase 2 spec):
//   {"px": 20}                        → LengthValue.Exact
//   {"px": 13.3, "original": {...}}   → LengthValue.Exact (absolute unit)
//   {"original": {"v": 2, "u":"EM"}}  → LengthValue.Relative(EM)
//   10.0                              → LengthValue.Relative(PERCENT)
//   {"expr": "calc(...)"}             → LengthValue.Calc

import com.styleconverter.runtime.core.types.LengthValue

/**
 * All 8 CSS padding longhands. A null side means "IR did not specify this
 * side" — the Applier leaves the platform default (0dp) in place. Logical
 * sides are resolved to physical at apply time via the current LayoutDirection.
 */
data class PaddingConfig(
    // Physical sides.
    val top: LengthValue? = null,
    val right: LengthValue? = null,
    val bottom: LengthValue? = null,
    val left: LengthValue? = null,
    // Logical sides. In LTR block flow: blockStart=top, blockEnd=bottom,
    // inlineStart=left, inlineEnd=right. In RTL they swap inline sides.
    val blockStart: LengthValue? = null,
    val blockEnd: LengthValue? = null,
    val inlineStart: LengthValue? = null,
    val inlineEnd: LengthValue? = null,
    // Wave-47 lane Z2 — the css-writing-modes-4 §6.4 side mapping for THIS
    // component's used writing mode, stored by PaddingExtractor and null
    // for every horizontal mode (LogicalSides.verticalOrNull's contract:
    // null keeps resolve() byte-identical to the legacy path below).
    val logicalSides: LogicalSides? = null,
) {
    /** True if at least one side was specified (any physical or logical). */
    val hasPadding: Boolean
        get() = top != null || right != null || bottom != null || left != null ||
            blockStart != null || blockEnd != null ||
            inlineStart != null || inlineEnd != null

    /**
     * Resolve logical→physical for the given text direction. Precedence:
     * physical side wins if both are set (matches CSS cascade tie-break).
     * Returns a simple 4-tuple so the Applier doesn't have to know about
     * logical directions.
     */
    fun resolve(isRtl: Boolean): Resolved {
        // Wave-47 lane Z2 — a stored VERTICAL mapping wins: under
        // vertical-lr `padding-block-start` is a LEFT padding
        // (css-writing-modes-4 §6.4), which the legacy lines below would
        // transpose to top. logicalSides is null for every horizontal mode
        // (extractor contract), so all pre-existing content takes the
        // legacy path byte-identically.
        logicalSides?.let { return resolveWith(it) }
        // In LTR: inlineStart=left, inlineEnd=right. In RTL: flipped.
        val start = if (isRtl) right else left
        val end = if (isRtl) left else right
        return Resolved(
            top = top ?: blockStart,
            right = right ?: (if (isRtl) inlineStart else inlineEnd) ?: end,
            bottom = bottom ?: blockEnd,
            left = left ?: (if (isRtl) inlineEnd else inlineStart) ?: start,
        )
    }

    /**
     * The table-driven resolve (twin of MarginConfig.resolveWith): each
     * physical side takes its declared physical value first, then whichever
     * logical side §6.4 maps onto it — a bijection, so exactly one logical
     * slot can feed each physical side.
     */
    private fun resolveWith(sides: LogicalSides): Resolved {
        // Which logical value lands on a given physical side under [sides].
        fun logicalFor(side: PhysicalSide): LengthValue? = when (side) {
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

    /** Flat physical-only view — never contains logical-side data. */
    data class Resolved(
        val top: LengthValue?,
        val right: LengthValue?,
        val bottom: LengthValue?,
        val left: LengthValue?,
    )
}
