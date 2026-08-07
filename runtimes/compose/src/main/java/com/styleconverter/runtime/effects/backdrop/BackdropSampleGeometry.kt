package com.styleconverter.runtime.effects.backdrop

import kotlin.math.max
import kotlin.math.min

/**
 * One crop of the pass-A backdrop image, expressed in two coordinate spaces.
 *
 * @param srcLeft/srcTop/width/height the rectangle to read from the pass-A
 *   bitmap (canvas pixel space, ALWAYS inside the bitmap's bounds).
 * @param dstLeft/dstTop where that crop lands in the element's own draw space
 *   (border-box local px, origin at the element's top-left). Zero whenever the
 *   border box is fully on the canvas — which is the normal case, since the
 *   sample rect IS the border box — and positive only when the box overhangs
 *   the canvas's top/left edge and the crop had to start inside it.
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
 * node ([localLeft]/[localTop]) and how big it is ([width]/[height] — the node
 * minus both margin bands on each axis).
 *
 * [localLeft]/[localTop] carry TWO contributions, both of them "steps that run
 * INSIDE this draw node and move the painted box":
 *  1. the resolved top/left MARGIN bands (step 4's `absolutePadding`), and
 *  2. the resolved POSITION offset (step 4's `absoluteOffset` — a relative /
 *     absolute / fixed / sticky box's used inset).
 * Both are pure translations of the element's paint relative to the draw
 * node's own origin, so they add, and every consumer (sample rect, patch
 * placement, border-box clip) needs the sum rather than either half.
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
 * THE SAMPLE RECT IS THE BORDER BOX — IT NEVER GROWS WITH σ (wave-34 lane B).
 *
 * filter-effects-2 §2 (Backdrop Filter Algorithm) clips the Backdrop Root
 * Image to the element's border box BEFORE the filter runs, and only then
 * asks the filter for its edge behaviour. Content outside the border box is
 * therefore NEVER an input: a blurred backdrop must not import the pixels
 * next to the box, only extensions of the box's own edge.
 *
 * Wave 26 shipped the opposite reading (grow the sample by the blur's 3σ
 * support, then cut back). Wave 34 refuted it with the corpus's own
 * boundary case — css/filter-effects/backdrop-filter-boundary.html, whose
 * six `.fg` boxes sit 5px inside a 160x90 `.bg` on a lime page and whose
 * WPT assertion is literally "No lime green should be brought in to the
 * blurred regions". Measured on the frozen wave33-final captures (mean
 * absolute error per tile against the Chrome 151 ref PNG, blur 3/6/12/24/
 * 48/96, `_diag34/laneB`):
 *
 *   grow-by-3σ model     1.6 / 3.6 / 80.5 / 19.7 / 25.6 / 99.3   ← wave 26
 *   border box + clamp   1.6 / 2.5 / 77.5 / 10.6 / 22.3 / 89.8
 *   border box + MIRROR  1.5 / 1.8 / 72.6 /  0.8 /  0.3 / 76.7   ← this file
 *   iOS capture          1.6 / 3.5 / 79.9 / 19.5 / 25.3 / 99.2
 *
 * The iOS row reproduces the grow-by-3σ row term for term, which is what
 * identifies the model as the cause; the web capture (Chrome's own
 * `backdrop-filter`) is byte-identical to the ref on four of the six tiles,
 * so the ref IS the browser's behaviour and not an artefact of the WPT
 * reference file's simulation. Tiles 3 and 6 carry a residual common to all
 * three platforms (the ref frame clips content at x=374, our composed canvas
 * at 390) that no filter model can move.
 *
 * The extension model is MIRROR, not clamp: the numbers above separate the
 * two, and the WPT reference file (`support/simulate-backdrop-blur.js`)
 * builds its expectation from `scale(-1)` copies of the element's own
 * border-box crop — Skia's `SkTileMode::kMirror`, which is what Chrome
 * hands the backdrop image.
 *
 * The split of responsibilities here:
 *
 *  - this file decides WHICH pixels exist (the border box, clamped to the
 *    backdrop image's real bounds — an element may hang off the canvas);
 *  - the painter's `TileMode.MIRROR` supplies everything outside that rect
 *    by reflecting it, which is both the spec's edge behaviour and the
 *    measured browser one.
 *
 * Everything here is integer pixel math with no Android types so it pins on
 * the JVM (BackdropSampleGeometryTest).
 */
object BackdropSampleGeometry {

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
     * @param positionOffsetXPx/[positionOffsetYPx] the element's resolved
     *   POSITION offset in px (`PositionApplier.resolvedOffset` — the very
     *   `absoluteOffset` step 4 is about to chain), 0 for a static box. Added
     *   to the local origin and NOT to the size: `Modifier.absoluteOffset`
     *   translates its child without resizing it, so the draw node's box stays
     *   the same shape and only its contents slide. SIGNED on purpose — a
     *   `right`/`bottom` inset resolves negative (PositionConfig.offsetX), and
     *   a negative `left` moves the box left; both must translate the patch
     *   the same way they translate the element's own background.
     * @return null when the border box is degenerate — margins alone can eat
     *   the node (a min-size floor plus a large margin), and a zero/negative
     *   box has nothing to sample. Null is a refusal, matching
     *   [sample]'s: nothing is painted rather than something plausible.
     *   The position offset can never trigger this: it does not touch the size.
     */
    fun borderBox(
        nodeWidth: Float,
        nodeHeight: Float,
        marginLeftPx: Float,
        marginTopPx: Float,
        marginRightPx: Float,
        marginBottomPx: Float,
        positionOffsetXPx: Float = 0f,
        positionOffsetYPx: Float = 0f,
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
        // The position offset rides on the ORIGIN only (see the param KDoc):
        // it slides the painted box inside an unchanged draw node, exactly
        // like the margin band does, so the two simply add.
        return BackdropBorderBox(
            localLeft = left + positionOffsetXPx,
            localTop = top + positionOffsetYPx,
            width = width,
            height = height,
        )
    }

    /**
     * Compute the crop for an element whose border box sits at
     * ([elemLeft], [elemTop]) with size [elemWidth]×[elemHeight] in the
     * backdrop image's pixel space, clamped into a [srcWidth]×[srcHeight]
     * backdrop image.
     *
     * There is deliberately NO pad parameter: filter-effects-2 §2 clips the
     * backdrop to the border box before filtering, so the sample rect is the
     * border box for every chain — see the file header for the measurement
     * that refuted the wave-26 grow-by-3σ reading.
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
        srcWidth: Int,
        srcHeight: Int,
    ): BackdropSample? {
        // Degenerate inputs: no box to filter, or no backdrop to read.
        if (elemWidth <= 0 || elemHeight <= 0) return null
        if (srcWidth <= 0 || srcHeight <= 0) return null

        // The border box in backdrop-image space, clamped into the image. The
        // clamp is what makes an element at the canvas edge legal: we read the
        // pixels that exist and let TileMode.MIRROR extend them, per the
        // spec's edge behaviour.
        val left = max(0, elemLeft)
        val top = max(0, elemTop)
        val right = min(srcWidth, elemLeft + elemWidth)
        val bottom = min(srcHeight, elemTop + elemHeight)

        // Fully off-canvas (or clamped to nothing) → nothing to sample.
        if (right <= left || bottom <= top) return null

        return BackdropSample(
            srcLeft = left,
            srcTop = top,
            width = right - left,
            height = bottom - top,
            // Element-local placement of the crop: how far the crop's origin
            // sits from the border box's origin. Zero for a box fully on the
            // canvas; positive only when the box overhangs the top/left edge
            // and the clamp above had to move the crop inwards.
            dstLeft = left - elemLeft,
            dstTop = top - elemTop,
        )
    }
}
