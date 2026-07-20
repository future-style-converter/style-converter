package com.styleconverter.runtime.scrolling

// Wave 18 RC4 — axis-selective overflow clipping.
//
// CSS lets the two overflow axes disagree: `overflow-x: clip` with
// `overflow-y: visible` must clip ink horizontally while letting it spill
// vertically (css-overflow-3 §3; WPT css-overflow clip-003). Compose's
// stock `Modifier.clip(RectangleShape)` clips BOTH axes via graphicsLayer,
// so single-axis configs need this drawing-level primitive instead.

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect

object AxisClip {

    /**
     * How far (px) the clip rect extends past the box on an UNCLIPPED axis.
     * DrawScope.clipRect takes Float edges and Skia rejects/degrades on
     * non-finite rects, so we cannot use Float.POSITIVE_INFINITY; 1e6 px is
     * a large FINITE bound, ~100x any capture surface, and is the pinned
     * cross-native constant (iOS AxisClipRect.unclippedExtent matches it).
     */
    const val UNCLIPPED_EXTENT: Float = 1_000_000f

    /**
     * Pure clip-rect geometry, split out so unit tests (and cross-native
     * probes against iOS `AxisClipRect.clipBounds(clipX:clipY:width:height:)`)
     * can pin it without a draw pass:
     *  - a CLIPPED axis is bounded by the box edge ([0, width] / [0, height]);
     *  - an UNCLIPPED axis is extended by ±[UNCLIPPED_EXTENT] so the
     *    intersecting canvas clip never bites on that axis.
     */
    fun clipBounds(clipX: Boolean, clipY: Boolean, width: Float, height: Float): Rect =
        Rect(
            // X axis: pin to the left box edge when clipping, else extend left.
            left = if (clipX) 0f else -UNCLIPPED_EXTENT,
            // Y axis: pin to the top box edge when clipping, else extend up.
            top = if (clipY) 0f else -UNCLIPPED_EXTENT,
            // Right edge mirrors left: box edge when clipped, extended when not.
            right = if (clipX) width else width + UNCLIPPED_EXTENT,
            // Bottom edge mirrors top: box edge when clipped, extended when not.
            bottom = if (clipY) height else height + UNCLIPPED_EXTENT
        )
}

/**
 * Clip this node's DRAWING (own ink + children, which paint inside the
 * node's content pass) to the box bounds on the selected axes only.
 *
 * Implementation: `Modifier.drawWithContent` + `DrawScope.clipRect` around
 * `drawContent()`. The canvas clip INTERSECTS the incoming clip, so the
 * huge extent on the visible axis leaves that axis effectively unclipped.
 * Crucially this affects PAINTING only — no layer, no layout change —
 * which is exactly CSS overflow-clip semantics (clipping never affects
 * layout, css-overflow-3 §3).
 */
fun Modifier.axisSelectiveClip(clipX: Boolean, clipY: Boolean): Modifier =
    drawWithContent {
        // Resolve the rect against the node's draw size at draw time (the
        // box can resize without recomposition; geometry must track it).
        val b = AxisClip.clipBounds(clipX, clipY, size.width, size.height)
        // Intersect the canvas clip with the axis rect for the content pass.
        clipRect(left = b.left, top = b.top, right = b.right, bottom = b.bottom) {
            // Draw the wrapped content (later modifiers + children) inside it.
            this@drawWithContent.drawContent()
        }
    }
