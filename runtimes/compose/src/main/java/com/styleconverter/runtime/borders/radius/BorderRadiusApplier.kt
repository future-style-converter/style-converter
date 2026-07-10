package com.styleconverter.runtime.borders.radius

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
        // and most-tested Shape; use it whenever every corner is x==y AND no
        // corner carries a paint-time percentage axis (fractions need the
        // custom Shape below to resolve against the laid-out size).
        if (config.isCircular && !config.hasFraction) {
            return modifier.clip(
                RoundedCornerShape(
                    topStart = config.topStart.first,
                    topEnd = config.topEnd.first,
                    bottomEnd = config.bottomEnd.first,
                    bottomStart = config.bottomStart.first
                )
            )
        }

        // Elliptical / percentage path: hand-built Shape that emits a
        // RoundRect with independent x/y radii per corner — this is what
        // CSS "40px 20px" and "50%" actually specify. Percentage axes are
        // resolved here against the box's width (x) / height (y) per
        // css-backgrounds-3 §4.4.
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

        // Convert each corner to pixel CornerRadius(x, y). A percentage
        // axis (carried as a 0..1 fraction) resolves against the box's
        // WIDTH for x and HEIGHT for y — css-backgrounds-3 §4.4. That's
        // why `border-radius: 50%` yields a full ellipse on a non-square
        // box (rx = w/2, ry = h/2), matching Chrome. Fixed axes convert
        // Dp→px via the Density Compose hands us here.
        fun cr(
            pair: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>,
            fraction: Pair<Float?, Float?>
        ) = with(density) {
            CornerRadius(
                fraction.first?.times(size.width) ?: pair.first.toPx(),
                fraction.second?.times(size.height) ?: pair.second.toPx()
            )
        }

        // Map logical corners (start/end) to physical corners (left/right)
        // based on layout direction. In LTR: start=left, end=right.
        val cTopStart = cr(config.topStart, config.topStartFraction)
        val cTopEnd = cr(config.topEnd, config.topEndFraction)
        val cBottomEnd = cr(config.bottomEnd, config.bottomEndFraction)
        val cBottomStart = cr(config.bottomStart, config.bottomStartFraction)
        val topLeft = if (ltr) cTopStart else cTopEnd
        val topRight = if (ltr) cTopEnd else cTopStart
        val bottomRight = if (ltr) cBottomEnd else cBottomStart
        val bottomLeft = if (ltr) cBottomStart else cBottomEnd

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
