package com.styleconverter.runtime.images

/**
 * ReplacedBoxSizing — CSS 2.1 §10.3.2 / §10.6.2 used-size rules for a REPLACED
 * element whose content now has an intrinsic size (wave-39 lane A2).
 *
 * PURE and platform-free on purpose: it is the one piece of this lane both
 * natives must agree on to the pixel, so it is unit-tested off-device on both
 * and its Swift twin (StyleEngine/images/ReplacedBoxSizing.swift) is a
 * line-for-line mirror. Nothing here touches Compose, a Bitmap, or a Modifier —
 * the caller turns a [Mode] into its platform's sizing call.
 *
 * ## The four cases, and where they come from
 *
 * CSS 2.1 gives `width: auto` / `height: auto` on a replaced element a
 * cascade of rules; with an intrinsic width, height AND ratio all available
 * (which is the case once the raster is decoded — css-images-3 §5.2 derives
 * the ratio from the raster's own dimensions) they collapse to four:
 *
 * | declared width | declared height | used content box                     |
 * |----------------|-----------------|--------------------------------------|
 * | definite       | definite        | exactly the declared box (§10.3.2 #1) |
 * | definite       | auto            | height = width ÷ ratio    (§10.6.2 #2)|
 * | auto           | definite        | width  = height × ratio   (§10.3.2 #2)|
 * | auto           | auto            | the intrinsic size        (§10.3.2 #4)|
 *
 * The two ratio rows are where the empty-box captures came from: with no
 * content there was no ratio, so a box with one declared axis had nothing to
 * derive the other from and collapsed.
 *
 * ## What it deliberately does NOT model
 *
 * min-/max-width/height clamping (§10.4) and `box-sizing: border-box`'s
 * padding subtraction are NOT applied here — those already live in the
 * platforms' own modifier chains, which run BEFORE the content is measured, so
 * duplicating them would apply each constraint twice. This function answers
 * only "given the box the chain produced, how big is the content", which is
 * exactly the boundary the caller sits on.
 */
object ReplacedBoxSizing {

    /** How the caller must size the replaced CONTENT inside the box its
     *  modifier chain already produced. */
    enum class Mode {
        /** Both axes definite — the content fills the box the chain sized.
         *  `object-fit` decides how the raster maps into it. */
        FILL_BOTH,

        /** Width definite, height auto — fill the width, derive the height
         *  from the intrinsic ratio (§10.6.2 rule 2). */
        WIDTH_FILLS_RATIO_HEIGHT,

        /** Height definite, width auto — fill the height, derive the width
         *  from the intrinsic ratio (§10.3.2 rule 2). */
        HEIGHT_FILLS_RATIO_WIDTH,

        /** Neither axis definite — the content is its intrinsic size and the
         *  box hugs it (§10.3.2 rule 4). This is the case the css-writing-modes
         *  intrinsic-size-contribution family asserts on. */
        INTRINSIC,
    }

    /**
     * Pick the mode for one replaced box.
     *
     * @param widthDefinite  the box's inline axis was given a used size by the
     *   style chain (a resolved length or a percentage — the platforms' own
     *   `hasDefiniteSize` answers this).
     * @param heightDefinite same, block axis.
     * @param aspectRatio    the content's intrinsic width ÷ height, or null
     *   when the raster is degenerate.
     *
     * A null ratio DOWNGRADES the two single-axis rows to [Mode.INTRINSIC]
     * rather than guessing: §10.3.2's rule 2 is written "if ... has an
     * intrinsic ratio", and its absence sends the used size to the intrinsic
     * dimension. Falling through to FILL_BOTH instead would stretch the raster
     * across an axis nothing declared — the exact silent distortion this
     * table exists to prevent.
     */
    fun mode(widthDefinite: Boolean, heightDefinite: Boolean, aspectRatio: Float?): Mode {
        val ratioUsable = aspectRatio != null && aspectRatio.isFinite() && aspectRatio > 0f
        return when {
            widthDefinite && heightDefinite -> Mode.FILL_BOTH
            widthDefinite && ratioUsable -> Mode.WIDTH_FILLS_RATIO_HEIGHT
            heightDefinite && ratioUsable -> Mode.HEIGHT_FILLS_RATIO_WIDTH
            else -> Mode.INTRINSIC
        }
    }
}
