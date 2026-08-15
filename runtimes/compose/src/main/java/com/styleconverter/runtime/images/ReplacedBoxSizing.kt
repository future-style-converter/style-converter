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
 * ## §10.4 constraint-violation sizing (wave-42 lane W6)
 *
 * [constrainAutoSize] adds CSS 2.1 §10.4's min-/max- resolution for the
 * BOTH-AXES-AUTO row: "for replaced elements with an intrinsic ratio and both
 * 'width' and 'height' specified as 'auto'", a min/max violation re-solves the
 * OTHER axis through the ratio via the spec's ten-row table. Without it the
 * css-ui box-sizing 010..025 family painted the delivered raster at intrinsic
 * size — box-sizing-016's 70px square rendered ~150px wide and wrapped (the
 * measured wave-41 T2 blocker recorded in tools/titan/svg-preraster.mjs).
 * `box-sizing: border-box`'s band subtraction still lives with the CALLER
 * (ReplacedImageContent), which alone can see padding/border extractors; this
 * object stays pure arithmetic on CONTENT-BOX px so both natives pin the same
 * numbers off-device.
 *
 * The single-declared-axis modes still delegate min/max to the platforms' own
 * modifier chains (which clamp the box), because a declared axis is never
 * re-solved by §10.4's replaced-element table — only the auto/auto row is.
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

    /** One §10.4-resolved used CONTENT size, px. A plain pair rather than a
     *  platform Size type so the Swift twin can mirror it field-for-field. */
    data class UsedSize(val widthPx: Float, val heightPx: Float)

    /**
     * CSS 2.1 §10.4 used width/height for a replaced element whose width AND
     * height are both `auto` — the [Mode.INTRINSIC] row, and ONLY that row.
     *
     * Inputs are all CONTENT-BOX px: the caller has already subtracted the
     * `box-sizing: border-box` padding+border band from each declared bound
     * (or passed the declared value verbatim under content-box). A null bound
     * means "not declared" (or declared `none`/unresolvable), i.e. 0 for the
     * mins and +∞ for the maxes — §10.4's initial values.
     *
     * The tentative used size is the intrinsic size itself (§10.3.2 rule 4 /
     * §10.6.2 rule 3 — a decoded raster always has both dimensions). Then:
     *
     *   * with a usable ratio, a violated bound re-solves the OTHER axis via
     *     the spec's ten-row table (the "Constraint Violation" table in
     *     §10.4), so max-height: 70px on a 1:1 raster yields 70×70 — the
     *     css-ui box-sizing-010..025 geometry;
     *   * without one (degenerate raster), each axis clamps INDEPENDENTLY —
     *     §10.4's non-ratio algorithm re-solves the violated axis with the
     *     bound as its computed value and leaves the other alone.
     *
     * With no bounds declared this is the identity on the intrinsic size, so
     * every pre-wave-42 caller (no min/max on the wire) renders byte-identical.
     */
    fun constrainAutoSize(
        intrinsicWidthPx: Float,
        intrinsicHeightPx: Float,
        aspectRatio: Float?,
        minWidthPx: Float? = null,
        maxWidthPx: Float? = null,
        minHeightPx: Float? = null,
        maxHeightPx: Float? = null,
    ): UsedSize {
        // Tentative used size = the intrinsic size (§10.3.2 rule 4).
        val w = intrinsicWidthPx
        val h = intrinsicHeightPx
        // §10.4 preamble: undeclared min is 0; negative px cannot reach the
        // wire but a defensive floor keeps the arithmetic total.
        val minW = (minWidthPx ?: 0f).coerceAtLeast(0f)
        val minH = (minHeightPx ?: 0f).coerceAtLeast(0f)
        // §10.4 (replaced-element algorithm): "use max(min, max)" — a max
        // below its min is raised to the min, never the other way round.
        val maxW = (maxWidthPx ?: Float.POSITIVE_INFINITY).coerceAtLeast(minW)
        val maxH = (maxHeightPx ?: Float.POSITIVE_INFINITY).coerceAtLeast(minH)
        // Same usability gate as [mode]: the table's ratio terms divide by the
        // tentative axes, so a degenerate raster must take the no-ratio path.
        val ratioUsable = aspectRatio != null && aspectRatio.isFinite() &&
            aspectRatio > 0f && w > 0f && h > 0f
        if (!ratioUsable) {
            // No ratio ⇒ nothing links the axes: clamp each independently
            // (re-solving a violated axis with the bound as computed value
            // degenerates to the clamp when no ratio can propagate it).
            return UsedSize(w.coerceIn(minW, maxW), h.coerceIn(minH, maxH))
        }
        // Which bounds does the tentative size violate? These four booleans
        // select the §10.4 table row; the two-violation rows must be tested
        // FIRST because the single-violation rows also match their inputs.
        val overW = w > maxW
        val underW = w < minW
        val overH = h > maxH
        val underH = h < minH
        return when {
            // Row 6/7 — both too large: shrink by the MOST violated axis
            // (the smaller scale factor), floor the derived axis at its min.
            overW && overH ->
                if (maxW / w <= maxH / h) UsedSize(maxW, kotlin.math.max(minH, maxW * h / w))
                else UsedSize(kotlin.math.max(minW, maxH * w / h), maxH)
            // Row 8/9 — both too small: grow by the MOST violated axis (the
            // larger scale factor), CAP the derived axis at its max. Both arms
            // read `min(max-…, …)`: §10.4 row 8 is "min(max-width,
            // min-height·w/h), min-height" and row 9 its mirror "min-width,
            // min(max-height, min-width·h/w)". The derived axis is already
            // ≥ its own min by construction (the driving axis is the MORE
            // violated one, so its scale factor over-satisfies the other min),
            // which is why the spec caps rather than floors here — flooring at
            // the min instead would let the derived axis blow straight past a
            // declared max (50×50 raster, min-width 100 / min-height 70 /
            // max-height 70 painted 100×100, not the ref's 100×70).
            underW && underH ->
                if (minW / w <= minH / h) UsedSize(kotlin.math.min(maxW, minH * w / h), minH)
                else UsedSize(minW, kotlin.math.min(maxH, minW * h / w))
            // Row 10 — squeezed in opposite directions: both bounds win and
            // the ratio is deliberately abandoned (the spec's own choice).
            underW && overH -> UsedSize(minW, maxH)
            overW && underH -> UsedSize(maxW, minH)
            // Rows 2..5 — one violated axis takes its bound; the other
            // re-solves through the ratio, then clamps to ITS opposite bound.
            overW -> UsedSize(maxW, kotlin.math.max(maxW * h / w, minH))
            underW -> UsedSize(minW, kotlin.math.min(minW * h / w, maxH))
            overH -> UsedSize(kotlin.math.max(maxH * w / h, minW), maxH)
            underH -> UsedSize(kotlin.math.min(minH * w / h, maxW), minH)
            // Row 1 — no violation: the tentative size IS the used size.
            else -> UsedSize(w, h)
        }
    }
}
