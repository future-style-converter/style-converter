package com.styleconverter.test.style.borders.radius

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Applies CSS border-radius to a Compose modifier.
 *
 * Two code paths, chosen by [BorderRadiusConfig.isCircular]:
 *   - Circular (x == y per corner): uses the built-in [RoundedCornerShape]
 *     which is cheaper and hits Compose's fast-path corner rasterizer.
 *   - Elliptical (x != y on any corner): uses a custom [Shape] that builds
 *     a [RoundRect] with per-corner [CornerRadius] (a, b) values. This is
 *     what CSS `border-radius: 40px 20px` actually means — and why the
 *     previous (Dp-per-corner) implementation silently collapsed elliptical
 *     pairs to just the horizontal radius.
 */
object BorderRadiusApplier {

    /**
     * Apply border radius by clipping the modifier to the corner shape.
     *
     * Clip is used (not outline) because Compose needs to clip background,
     * content, and child composables to the rounded boundary — matching the
     * CSS paint model where the border box defines the visible region.
     */
    fun applyRadius(modifier: Modifier, config: BorderRadiusConfig): Modifier {
        // Zero-radius fast path: no Shape allocation, no clip pass.
        if (!config.hasRadius) return modifier

        // Circular fast path: the built-in RoundedCornerShape is the cheapest
        // and most-tested Shape; use it whenever every corner is x==y.
        if (config.isCircular) {
            return modifier.clip(
                RoundedCornerShape(
                    topStart = config.topStart.first,
                    topEnd = config.topEnd.first,
                    bottomEnd = config.bottomEnd.first,
                    bottomStart = config.bottomStart.first
                )
            )
        }

        // Elliptical path: hand-built Shape that emits a RoundRect with
        // independent x/y radii per corner — this is what CSS "40px 20px"
        // actually specifies and what the IR's {horizontal, vertical}
        // encoding carries through.
        return modifier.clip(EllipticalCornerShape(config))
    }
}

/**
 * Custom [Shape] that produces elliptical per-corner radii by building a
 * [RoundRect] with eight independent CornerRadius components.
 *
 * Compose's [RoundedCornerShape] only accepts a single Dp per corner and
 * uses it for both axes — insufficient for CSS semantics. This Shape
 * mirrors the standard Compose corner-shape API (including RTL handling
 * via [LayoutDirection]) so it drops into `Modifier.clip` transparently.
 */
private class EllipticalCornerShape(
    private val config: BorderRadiusConfig
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Resolve logical start/end to physical left/right at paint time —
        // this matches how RoundedCornerShape handles RTL, so our custom
        // shape behaves consistently with the rest of the style engine.
        val ltr = layoutDirection == LayoutDirection.Ltr

        // Convert each Dp pair to pixel CornerRadius(x, y). Dp->px needs
        // the current Density, which Compose hands us here.
        fun cr(pair: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>) =
            with(density) {
                CornerRadius(pair.first.toPx(), pair.second.toPx())
            }

        // Map logical corners (start/end) to physical corners (left/right)
        // based on layout direction. In LTR: start=left, end=right.
        val topLeft = if (ltr) cr(config.topStart) else cr(config.topEnd)
        val topRight = if (ltr) cr(config.topEnd) else cr(config.topStart)
        val bottomRight = if (ltr) cr(config.bottomEnd) else cr(config.bottomStart)
        val bottomLeft = if (ltr) cr(config.bottomStart) else cr(config.bottomEnd)

        // RoundRect with per-corner CornerRadius(x, y) — this is the only
        // geometry type in Compose that represents true elliptical corners.
        val rr = RoundRect(
            rect = Rect(0f, 0f, size.width, size.height),
            topLeft = topLeft,
            topRight = topRight,
            bottomRight = bottomRight,
            bottomLeft = bottomLeft
        )
        // Wrap in a Path so the Outline accepts a non-convex rounded rect
        // (Outline.Rounded requires a convex RoundRect, which ours always
        // is, but Path works for any clip target and is just as fast after
        // the first rasterization).
        return Outline.Generic(Path().apply { addRoundRect(rr) })
    }
}
