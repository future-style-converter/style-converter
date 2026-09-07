package com.styleconverter.runtime.effects.clip

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import kotlin.math.min

/**
 * The radial `<basic-shape>` factories — `circle()` and `ellipse()` —
 * split out of [ClipPathApplier] (wave 46, lane Y4) to keep each file
 * under the house size target. Every coordinate below is REFERENCE-BOX
 * space (css-masking-1 §5.1): centres offset from the box's top-left,
 * radius keywords / percents against its extents, the box itself
 * resolved at draw time through [ClipPathApplier.ClipRefBox.frame].
 */
internal object ClipRadialShapes {
    /**
     * Resolve one axis of a basic shape's `at <position>` centre.
     *
     * CSS Shapes 1 §3.1 takes a css-values-4 `<position>`, whose
     * `[left|right|top|bottom] <length-percentage>` arm measures the offset
     * from the NAMED edge. The converter normalises every form it can to
     * the default (left / top) origin and sets `pos.xEdge` / `pos.yEdge`
     * only for the right/bottom-anchored offsets it cannot — those need the
     * box extent, which is only known here (wave 37 lane W3; before it the
     * converter dropped keyword `at` clauses entirely, so this arm never
     * reached any runtime).
     *
     * @param dp absolute offset from that edge, when the IR carried a px
     *   length; wins over [percent] exactly as it did before this wave.
     * @param percent offset as 0..100 of the box extent.
     * @param fromFarEdge true when the offset is measured from the right /
     *   bottom edge rather than the left / top one.
     */
    fun axisCenter(
        dp: androidx.compose.ui.unit.Dp?,
        percent: Float,
        fromFarEdge: Boolean,
        extent: Float,
        density: Density,
    ): Float {
        val offset = dp?.let { with(density) { it.toPx() } } ?: (extent * (percent / 100f))
        return if (fromFarEdge) extent - offset else offset
    }

    /**
     * Create a Compose Shape for circle clip-path.
     */
    fun createCircleShape(circle: ClipShape.Circle, ref: ClipPathApplier.ClipRefBox): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // Everything below is in REFERENCE-BOX space (css-masking-1
                // §7.1): the centre offsets from its top-left, the radius
                // keywords / percents against its extents.
                val box = ref.frame(size, density).rect
                // Absolute-length center (`at 20px 30px`) wins over the
                // percent form when present — CSS Shapes 1 §3.1 <position>.
                val centerX = axisCenter(
                    circle.centerXDp, circle.centerX, circle.centerFromRight, box.width, density,
                )
                val centerY = axisCenter(
                    circle.centerYDp, circle.centerY, circle.centerFromBottom, box.height, density,
                )

                val radius = resolveCircleRadius(
                    circle.radius,
                    box.size,
                    centerX,
                    centerY,
                    density
                )

                val path = Path().apply {
                    addOval(
                        Rect(
                            left = box.left + centerX - radius,
                            top = box.top + centerY - radius,
                            right = box.left + centerX + radius,
                            bottom = box.top + centerY + radius
                        )
                    )
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Resolve circle radius based on radius type and element size.
     */
    fun resolveCircleRadius(
        radius: ClipRadius,
        size: Size,
        centerX: Float,
        centerY: Float,
        density: Density
    ): Float {
        return when (radius) {
            is ClipRadius.Fixed -> with(density) { radius.dp.toPx() }
            // CSS Shapes 1 §3.1: a percentage circle radius resolves
            // against sqrt(width² + height²) / √2 — NOT min(w,h). For the
            // 358×70 ClipPath_Circle_AtCenter box, circle(40%): spec gives
            // 0.4·√(358²+70²)/√2 ≈ 103px; the old min(w,h)·0.4 = 28px
            // clipped a far smaller disc than web (SSIM 0.90, 4.9% px).
            is ClipRadius.Percentage ->
                (kotlin.math.sqrt(size.width * size.width + size.height * size.height.toDouble()) /
                    kotlin.math.sqrt(2.0)).toFloat() * (radius.percent / 100f)
            ClipRadius.ClosestSide -> min(
                min(centerX, size.width - centerX),
                min(centerY, size.height - centerY)
            )
            ClipRadius.FarthestSide -> maxOf(
                maxOf(centerX, size.width - centerX),
                maxOf(centerY, size.height - centerY)
            )
        }
    }

    /**
     * Create a Compose Shape for ellipse clip-path.
     */
    fun createEllipseShape(ellipse: ClipShape.Ellipse, ref: ClipPathApplier.ClipRefBox): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // Reference-box space, as for the circle above.
                val box = ref.frame(size, density).rect
                val centerX = axisCenter(
                    ellipse.centerXDp, ellipse.centerX, ellipse.centerFromRight, box.width, density,
                )
                val centerY = axisCenter(
                    ellipse.centerYDp, ellipse.centerY, ellipse.centerFromBottom, box.height, density,
                )

                val radiusX = resolveEllipseRadius(
                    ellipse.radiusX,
                    box.size,
                    centerX,
                    centerY,
                    density,
                    isHorizontal = true
                )
                val radiusY = resolveEllipseRadius(
                    ellipse.radiusY,
                    box.size,
                    centerX,
                    centerY,
                    density,
                    isHorizontal = false
                )

                val path = Path().apply {
                    addOval(
                        Rect(
                            left = box.left + centerX - radiusX,
                            top = box.top + centerY - radiusY,
                            right = box.left + centerX + radiusX,
                            bottom = box.top + centerY + radiusY
                        )
                    )
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Resolve ellipse radius based on radius type, axis, and element size.
     */
    fun resolveEllipseRadius(
        radius: ClipRadius,
        size: Size,
        centerX: Float,
        centerY: Float,
        density: Density,
        isHorizontal: Boolean
    ): Float {
        return when (radius) {
            is ClipRadius.Fixed -> with(density) { radius.dp.toPx() }
            is ClipRadius.Percentage -> {
                if (isHorizontal) size.width * (radius.percent / 100f)
                else size.height * (radius.percent / 100f)
            }
            ClipRadius.ClosestSide -> {
                if (isHorizontal) min(centerX, size.width - centerX)
                else min(centerY, size.height - centerY)
            }
            ClipRadius.FarthestSide -> {
                if (isHorizontal) maxOf(centerX, size.width - centerX)
                else maxOf(centerY, size.height - centerY)
            }
        }
    }

}
