package com.styleconverter.runtime.sizing

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

import com.styleconverter.runtime.core.types.LengthValue

/**
 * css-sizing-3 §3 `box-sizing` keyword. Only the two spec values live here —
 * the TRI-STATE the sizing lane needs (unset ≠ content-box!) is carried by
 * nullability on [SizingConfig.boxSizing]: null means "the IR never declared
 * box-sizing", which must keep today's border-box modifier chains byte-stable.
 * (The performance/ lane's BoxModelConfig defaults its copy to CONTENT_BOX —
 * that no-op default must never leak into sizing, hence a separate tri-state
 * here instead of threading BoxModelConfig through.)
 */
enum class BoxSizingKeyword {
    /** Declared width/height = content box; frame = content + padding + border. */
    CONTENT_BOX,

    /** Declared width/height = border box (the pre-existing chain behaviour). */
    BORDER_BOX,
}

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
    // css-values-5 calc-size() typed values (wave 42 lane W3) — the four
    // slots the converter types today (physical width/height + their min
    // twins). Kept BESIDE the LengthValue slots rather than widening the
    // shared core LengthValue: a slot carries EITHER its LengthValue OR its
    // calc-size value, never both (SizingExtractor routes exclusively).
    // Max* calc-size values are deliberately NOT carried: the applier's
    // documented behavior for an unresolvable max bound is "no constraint",
    // which is exactly what the undecoded slot already yields — see the
    // extractor's Max* comment for the non-silent record.
    val widthCalc: CalcSizeValue? = null,
    val heightCalc: CalcSizeValue? = null,
    val minWidthCalc: CalcSizeValue? = null,
    val minHeightCalc: CalcSizeValue? = null,
    // css-sizing-3 §3 `box-sizing`. Null = the IR never declared it — the
    // border-box status quo (the whole width+padding fixture corpus is
    // captured against web's `* { box-sizing: border-box }` reset) must not
    // change. Only an EXPLICIT CONTENT_BOX makes the Applier inflate
    // declared width/height by [contentBoxInflateX]/[contentBoxInflateY].
    val boxSizing: BoxSizingKeyword? = null,
    // Pre-resolved content-box frame inflation per axis (px): CSS padding
    // band + used border widths. Computed by SizingExtractor ONLY when
    // boxSizing == CONTENT_BOX (0f otherwise) so the Applier stays a pure
    // SizingConfig → Modifier function with no extractor re-runs.
    val contentBoxInflateX: Float = 0f,
    val contentBoxInflateY: Float = 0f,
    // RC-B6b (TITAN WPT lane) — true only when this config was extracted on
    // the LocalWptCaptureMode capture path (SizingExtractor threads it, the
    // same plumbing as the boxSizing tri-state above). The Applier reads it
    // to route px-RESOLVABLE relative widths (ch/em/rem/vw — block-ellipsis
    // -001's `width: 63.1ch` ≈ 605px) through the wave-12 overflow-aware
    // exactWidth instead of Modifier.width, whose constraint COERCION
    // clamped the box to the incoming max and shifted every text wrap
    // point vs the browser ref. Default false = the dark-stage/327-pair
    // corpus keeps the coercing Modifier.width byte-identically.
    val wptCaptureMode: Boolean = false,
) {
    /**
     * True if any sizing/aspect-ratio slot was populated. [boxSizing] is
     * deliberately EXCLUDED: box-sizing only changes how definite
     * width/height resolve (css-sizing-3 §3) — with no size to
     * reinterpret the Applier has nothing to do, so a lone box-sizing
     * declaration must not force sizing modifiers onto the chain.
     */
    val hasSizing: Boolean
        get() = width != null || height != null ||
            minWidth != null || maxWidth != null ||
            minHeight != null || maxHeight != null ||
            blockSize != null || inlineSize != null ||
            minBlockSize != null || maxBlockSize != null ||
            minInlineSize != null || maxInlineSize != null ||
            aspectRatio != null ||
            // calc-size slots size the box too — without these the applier's
            // fast path would skip a component whose ONLY sizing is typed
            // calc-size (exactly the calc-size-flex item shape).
            widthCalc != null || heightCalc != null ||
            minWidthCalc != null || minHeightCalc != null

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
