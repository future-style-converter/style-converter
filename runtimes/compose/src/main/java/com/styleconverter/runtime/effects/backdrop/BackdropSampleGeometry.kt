package com.styleconverter.runtime.effects.backdrop

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * One crop of the pass-A backdrop image, expressed in two coordinate spaces.
 *
 * @param srcLeft/srcTop/width/height the rectangle to read from the pass-A
 *   bitmap (canvas pixel space, ALWAYS inside the bitmap's bounds).
 * @param dstLeft/dstTop where that crop lands in the element's own draw space
 *   (border-box local px, origin at the element's top-left). Negative when the
 *   blur padding reaches outside the element, which is exactly the point: the
 *   painter draws the padded patch and lets the border-box clip cut it back,
 *   so pixels from OUTSIDE the box still feed the Gaussian.
 */
data class BackdropSample(
    val srcLeft: Int,
    val srcTop: Int,
    val width: Int,
    val height: Int,
    val dstLeft: Int,
    val dstTop: Int,
)

/**
 * The element's BORDER box expressed in its own draw space.
 *
 * The backdrop draw node is installed at StyleApplier step 3 (effects), which
 * is OUTER of the margin step (step 4 — LayoutFacade → MarginApplier emits the
 * positive margins as `Modifier.absolutePadding`). A draw modifier's `size` is
 * therefore the MARGIN box, and its `positionInWindow()` the margin box's
 * origin — while filter-effects-2 §2 samples and clips the BORDER box.
 *
 * This value is the difference: where the border box starts inside the draw
 * node ([localLeft]/[localTop] — the resolved top/left margins) and how big it
 * is ([width]/[height] — the node minus both margin bands on each axis).
 */
data class BackdropBorderBox(
    val localLeft: Float,
    val localTop: Float,
    val width: Float,
    val height: Float,
)

/**
 * Pure geometry for "what part of the backdrop does this element see".
 *
 * filter-effects-2 §2: the filtered region is the Backdrop Root Image
 * restricted to the element's border box, and the filter operates on that
 * image with the backdrop root's edges duplicated (edge clamp) — a blurred
 * backdrop must not fade into transparency at the root boundary. The split of
 * responsibilities here:
 *
 *  - this file decides WHICH pixels are available (a padded crop, clamped to
 *    the backdrop image's real bounds);
 *  - the painter's `TileMode.CLAMP` supplies what lies past that clamp by
 *    duplicating edge pixels — the spec's edge behaviour, and the reason the
 *    crop is allowed to be smaller than the requested padded rect.
 *
 * Everything here is integer pixel math with no Android types so it pins on
 * the JVM (BackdropSampleGeometryTest).
 */
object BackdropSampleGeometry {

    /**
     * How far outside the border box a blur of standard deviation [sigmaPx]
     * can still pull visible energy from: 3σ, the conventional Gaussian
     * truncation (>99.7% of the kernel mass). Rounded UP so the sample never
     * comes up a pixel short of what the kernel reads; 0 for a chain with no
     * blur, which collapses the sample to the border box exactly.
     */
    fun blurPadPx(sigmaPx: Float): Int =
        if (sigmaPx <= 0f) 0 else ceil(3.0 * sigmaPx).toInt()

    /**
     * Strip the resolved MARGIN bands off the backdrop draw node's box, so
     * everything downstream (sample rect, patch placement, border-box clip)
     * works in the border box the spec names.
     *
     * @param nodeWidth/[nodeHeight] the draw node's own size in px — the
     *   margin box, because the effects step is outer of the margin step.
     * @param marginLeftPx…[marginBottomPx] the resolved POSITIVE margin on
     *   each physical side, in px. Positive-only on purpose: MarginApplier
     *   inflates the layout box with `absolutePadding` for positive sides and
     *   translates with `offset` for negative ones — an offset moves the whole
     *   node (patch included) and does NOT change its size, so a negative
     *   margin contributes nothing to subtract here.
     * @return null when the border box is degenerate — margins alone can eat
     *   the node (a min-size floor plus a large margin), and a zero/negative
     *   box has nothing to sample. Null is a refusal, matching
     *   [sample]'s: nothing is painted rather than something plausible.
     */
    fun borderBox(
        nodeWidth: Float,
        nodeHeight: Float,
        marginLeftPx: Float,
        marginTopPx: Float,
        marginRightPx: Float,
        marginBottomPx: Float,
    ): BackdropBorderBox? {
        // Clamp each band at 0 so a caller that hands us a negative value
        // (or a Dp that resolved below zero) can never GROW the border box.
        val left = if (marginLeftPx > 0f) marginLeftPx else 0f
        val top = if (marginTopPx > 0f) marginTopPx else 0f
        val right = if (marginRightPx > 0f) marginRightPx else 0f
        val bottom = if (marginBottomPx > 0f) marginBottomPx else 0f
        // What is left of the node once both bands on an axis are removed.
        val width = nodeWidth - left - right
        val height = nodeHeight - top - bottom
        if (width <= 0f || height <= 0f) return null
        return BackdropBorderBox(localLeft = left, localTop = top, width = width, height = height)
    }

    /**
     * Compute the crop for an element whose border box sits at
     * ([elemLeft], [elemTop]) with size [elemWidth]×[elemHeight] in the
     * backdrop image's pixel space, padded by [padPx] on every side and
     * clamped into a [srcWidth]×[srcHeight] backdrop image.
     *
     * Returns null when there is nothing to sample — a degenerate element
     * (zero width/height: `backdrop-filter-zero-size.html`), a degenerate
     * backdrop, or a border box entirely off the canvas. Null is a refusal,
     * not a fallback: the painter draws nothing and the element paints its own
     * box exactly as it would without this lane.
     */
    fun sample(
        elemLeft: Int,
        elemTop: Int,
        elemWidth: Int,
        elemHeight: Int,
        padPx: Int,
        srcWidth: Int,
        srcHeight: Int,
    ): BackdropSample? {
        // Degenerate inputs: no box to filter, or no backdrop to read.
        if (elemWidth <= 0 || elemHeight <= 0) return null
        if (srcWidth <= 0 || srcHeight <= 0) return null

        // Requested (padded) rect in backdrop-image space, then clamped into
        // the image. The clamp is what makes an element at the canvas edge
        // legal: we read the pixels that exist and let TileMode.CLAMP
        // extend them, per the spec's edge-duplication rule.
        val left = max(0, elemLeft - padPx)
        val top = max(0, elemTop - padPx)
        val right = min(srcWidth, elemLeft + elemWidth + padPx)
        val bottom = min(srcHeight, elemTop + elemHeight + padPx)

        // Fully off-canvas (or clamped to nothing) → nothing to sample.
        if (right <= left || bottom <= top) return null

        return BackdropSample(
            srcLeft = left,
            srcTop = top,
            width = right - left,
            height = bottom - top,
            // Element-local placement of the crop: how far the crop's origin
            // sits from the border box's origin. Zero for an unpadded,
            // fully-inside sample; negative once padding reaches left/up.
            dstLeft = left - elemLeft,
            dstTop = top - elemTop,
        )
    }
}
