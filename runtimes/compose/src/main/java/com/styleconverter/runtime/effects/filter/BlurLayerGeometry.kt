package com.styleconverter.runtime.effects.filter

// Pure geometry only — androidx.compose.ui.geometry.Rect is plain Kotlin, so
// this file runs in the JVM unit suite (no Robolectric here; android.graphics
// is a throwing stub). The draw-time half (GraphicsLayer, RenderEffect, the
// canvas clip read) stays in BlurLayerNode.apply.
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Sizing of the offscreen layer `filter: blur()` records into — the
 * JVM-testable half of [BlurLayerNode.apply] (retro R6, audit
 * finding A12#2: Android dropped every descendant pixel outside the element's
 * layout bounds under blur, because `Modifier.blur`'s RenderNode is sized to
 * those bounds and HWUI renders a RenderEffect node into a buffer of exactly
 * that size).
 *
 * The contract is the one color/OpacityApplier's `saveLayerAlpha(null, α)`
 * already gives the `opacity` property ("the layer covers the current clip"):
 * the blur layer must cover every pixel the element's subtree can land on
 * inside the visible canvas, i.e. the canvas' current clip bounds — UNION the
 * node's own box (an empty or off-node clip must still blur the box itself)
 * — inflated by the Gaussian's reach so ink just outside the clip still
 * contributes to pixels just inside it.
 */
internal object BlurLayerGeometry {

    /**
     * The layer rectangle in the draw node's LOCAL space plus its integer
     * pixel size. [left]/[top] are the offsets the content is translated by
     * (negated) while recording and the layer is translated by when drawn.
     * [coversClip] is false when the layer had to fall back to the node box
     * (no clip, or a clip larger than [MAX_LAYER_DIM]) — the breadcrumb the
     * applier logs so a crop is never silent.
     */
    data class LayerBounds(
        val left: Float,
        val top: Float,
        val width: Int,
        val height: Int,
        val coversClip: Boolean,
    )

    /**
     * Largest layer edge we will ask HWUI for. A RenderNode layer is a GPU
     * texture; 8192 is the conservative floor of GL_MAX_TEXTURE_SIZE on the
     * emulator/host GPUs this runtime targets. A canvas taller than that
     * (the corpus's degenerate 9470px Android canvas, BACKLOG queue 1c) falls
     * back to the node box rather than failing to allocate.
     */
    const val MAX_LAYER_DIM = 8192

    /**
     * How far past a rectangle the layer must extend for a Gaussian of
     * standard deviation [sigmaPx]: 3σ holds 99.7 % of the kernel's mass, so
     * ink beyond it cannot move a visible pixel by a full 8-bit step.
     * Rounded UP to whole pixels so the layer origin stays pixel-aligned
     * (a fractional translate would resample the blurred layer).
     */
    fun inflation(sigmaPx: Float): Float = ceil(3f * sigmaPx.coerceAtLeast(0f))

    /**
     * Compute the layer bounds for a node of [nodeWidthPx]×[nodeHeightPx]
     * whose canvas currently clips to [clip] (node-local coordinates, as
     * `android.graphics.Canvas.getClipBounds` reports them; null when the
     * clip is empty / unavailable), blurring with [sigmaPx].
     *
     * Result rect = (clip ∪ node box) inflated by [inflation], snapped
     * outward to whole pixels. When that exceeds [maxDim] on either axis —
     * or there is no clip to read — the node box alone (still inflated) is
     * used and [LayerBounds.coversClip] reports the fallback.
     */
    fun layerBounds(
        nodeWidthPx: Float,
        nodeHeightPx: Float,
        clip: Rect?,
        sigmaPx: Float,
        maxDim: Int = MAX_LAYER_DIM,
    ): LayerBounds {
        // Gaussian reach, whole pixels (see inflation).
        val pad = inflation(sigmaPx)
        // The node's own box in its local space — always covered.
        val node = Rect(0f, 0f, nodeWidthPx, nodeHeightPx)
        // Snap a rect outward to the pixel lattice and size it (≥ 1 px so a
        // degenerate node never asks for an empty layer).
        fun snapped(r: Rect, covers: Boolean): LayerBounds {
            val left = floor(r.left - pad)
            val top = floor(r.top - pad)
            val right = ceil(r.right + pad)
            val bottom = ceil(r.bottom + pad)
            return LayerBounds(
                left = left,
                top = top,
                width = (right - left).toInt().coerceAtLeast(1),
                height = (bottom - top).toInt().coerceAtLeast(1),
                coversClip = covers,
            )
        }
        // No usable clip → node box only (documented fallback, not a crop of
        // anything visible: with no clip there is nothing to cover).
        if (clip == null || clip.isEmpty) return snapped(node, covers = false)
        // Union with the node box: a clip that excludes part of the node must
        // not shrink the layer below the node (the blur of the box's own edge
        // would then be cut), and a clip beyond the node is exactly the
        // overflow region this exists to keep.
        val union = Rect(
            left = min(clip.left, node.left),
            top = min(clip.top, node.top),
            right = max(clip.right, node.right),
            bottom = max(clip.bottom, node.bottom),
        )
        val full = snapped(union, covers = true)
        // Texture-size guard — see MAX_LAYER_DIM.
        if (full.width > maxDim || full.height > maxDim) return snapped(node, covers = false)
        return full
    }
}
