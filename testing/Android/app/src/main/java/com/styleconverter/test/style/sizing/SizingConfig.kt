package com.styleconverter.test.style.sizing

// Phase 3: SizingConfig now stores every sizing side as a LengthValue? so that
// %/em/vw/calc shapes survive to the Applier. Width/Height/InlineSize/BlockSize
// still honour min/max-content, auto, and fit-content(<bound>). The new None
// variant is used by MaxWidth/MaxHeight when the CSS author wrote `none`.
//
// The old SizeValue sealed interface (Fixed/Percentage/FillMax/WrapContent/Auto)
// is removed — all callers funneled through SizingApplier.applySizing and are
// unaffected by the internal representation swap.
//
// IR shapes reaching each field (see Phase 3 spec):
//   Width/Height           : WidthValue shape → LengthValue (Exact/Relative/Auto/Intrinsic)
//   MinWidth/MaxWidth …    : WidthValue + "none" → LengthValue (+ None)
//   BlockSize/InlineSize   : SizeValue shape (raw IRLength or bare number)
//   MinBlockSize/…/MaxI…   : SizeValue shape + "none"
//   AspectRatio            : its own AspectRatioValue type

import com.styleconverter.test.style.core.types.LengthValue

/**
 * All sizing properties collected off one component. A null slot means the IR
 * did not specify that side — the Applier leaves Compose defaults in place.
 * [aspectRatio] is null when the author did not set aspect-ratio at all.
 */
data class SizingConfig(
    // Physical sizing (CSS width/height).
    val width: LengthValue? = null,
    val height: LengthValue? = null,
    // Constraints. `None` here distinguishes explicit `max-width: none` from
    // "not specified" — both collapse to "no upper bound" at apply time, but
    // keeping the variant means other tooling can tell them apart.
    val minWidth: LengthValue? = null,
    val maxWidth: LengthValue? = null,
    val minHeight: LengthValue? = null,
    val maxHeight: LengthValue? = null,
    // Logical sides. LTR block flow: blockSize=height, inlineSize=width.
    val blockSize: LengthValue? = null,
    val inlineSize: LengthValue? = null,
    val minBlockSize: LengthValue? = null,
    val maxBlockSize: LengthValue? = null,
    val minInlineSize: LengthValue? = null,
    val maxInlineSize: LengthValue? = null,
    // aspect-ratio is its own shape.
    val aspectRatio: AspectRatioValue? = null,
) {
    /** True if any sizing/aspect-ratio slot was populated. */
    val hasSizing: Boolean
        get() = width != null || height != null ||
            minWidth != null || maxWidth != null ||
            minHeight != null || maxHeight != null ||
            blockSize != null || inlineSize != null ||
            minBlockSize != null || maxBlockSize != null ||
            minInlineSize != null || maxInlineSize != null ||
            aspectRatio != null

    /**
     * True when any width-direction constraint is specified. Used by callers
     * that want to apply width-only sizing (e.g. flex item width).
     */
    val hasWidthConstraints: Boolean
        get() = width != null || minWidth != null || maxWidth != null ||
            inlineSize != null || minInlineSize != null || maxInlineSize != null

    /** True when any height-direction constraint is specified. */
    val hasHeightConstraints: Boolean
        get() = height != null || minHeight != null || maxHeight != null ||
            blockSize != null || minBlockSize != null || maxBlockSize != null
}
