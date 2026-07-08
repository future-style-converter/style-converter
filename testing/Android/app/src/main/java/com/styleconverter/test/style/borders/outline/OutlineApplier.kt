package com.styleconverter.test.style.borders.outline

import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies CSS outline styling to Compose modifiers.
 *
 * CSS outline is drawn OUTSIDE the element bounds, with optional offset.
 * This is different from border which is drawn inside/on the bounds.
 *
 * ## Implementation Notes
 * - Uses drawBehind with native Canvas for precise control
 * - Outlines are drawn outside the element bounds using negative insets
 * - DASHED and DOTTED styles use DashPathEffect
 * - DOUBLE style draws two separate outlines
 * - GROOVE, RIDGE, INSET, OUTSET fall back to SOLID (no 3D effect support)
 *
 * ## Compose Limitations
 * - No built-in outline support, requires custom drawing
 * - Native Canvas required for DashPathEffect
 */
object OutlineApplier {

    /**
     * Apply outline to modifier.
     *
     * The outline is drawn outside the element bounds at a distance
     * determined by [OutlineConfig.offset].
     *
     * @param modifier The modifier to apply outline to.
     * @param config The outline configuration.
     * @return Modified modifier with outline drawing.
     */
    fun applyOutline(modifier: Modifier, config: OutlineConfig): Modifier {
        if (!config.hasOutline) return modifier

        return modifier.drawBehind {
            val widthPx = config.width.toPx()
            val offsetPx = config.offset.toPx()
            // Position outline center at: element edge + offset + half width
            val totalOffset = widthPx / 2 + offsetPx

            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = widthPx
                color = config.color.toArgb()
                isAntiAlias = true

                // Apply dash pattern based on style
                pathEffect = when (config.style) {
                    OutlineStyle.DASHED -> DashPathEffect(floatArrayOf(widthPx * 3, widthPx * 2), 0f)
                    OutlineStyle.DOTTED -> DashPathEffect(floatArrayOf(widthPx, widthPx), 0f)
                    else -> null
                }
            }

            // Draw rectangle outside the bounds
            drawContext.canvas.nativeCanvas.drawRect(
                -totalOffset,
                -totalOffset,
                size.width + totalOffset,
                size.height + totalOffset,
                paint
            )

            // For double style, draw a second outline further out
            if (config.style == OutlineStyle.DOUBLE && widthPx >= 3f) {
                val outerOffset = totalOffset + widthPx * 2 / 3
                paint.strokeWidth = widthPx / 3
                paint.pathEffect = null
                drawContext.canvas.nativeCanvas.drawRect(
                    -outerOffset,
                    -outerOffset,
                    size.width + outerOffset,
                    size.height + outerOffset,
                    paint
                )
            }
        }
    }

    /**
     * Apply outline with rounded corners.
     *
     * When the element has border-radius, the outline should follow
     * the rounded shape at an offset distance.
     *
     * @param modifier The modifier to apply outline to.
     * @param config The outline configuration.
     * @param cornerRadius The border-radius of the element.
     * @return Modified modifier with rounded outline drawing.
     */
    fun applyOutlineWithRadius(
        modifier: Modifier,
        config: OutlineConfig,
        cornerRadius: Dp = 0.dp
    ): Modifier {
        if (!config.hasOutline) return modifier

        return modifier.drawBehind {
            val widthPx = config.width.toPx()
            val offsetPx = config.offset.toPx()
            val totalOffset = widthPx / 2 + offsetPx
            // Increase radius to maintain visual continuity at offset distance
            val radiusPx = cornerRadius.toPx() + totalOffset

            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = widthPx
                color = config.color.toArgb()
                isAntiAlias = true

                pathEffect = when (config.style) {
                    OutlineStyle.DASHED -> DashPathEffect(floatArrayOf(widthPx * 3, widthPx * 2), 0f)
                    OutlineStyle.DOTTED -> DashPathEffect(floatArrayOf(widthPx, widthPx), 0f)
                    else -> null
                }
            }

            drawContext.canvas.nativeCanvas.drawRoundRect(
                -totalOffset,
                -totalOffset,
                size.width + totalOffset,
                size.height + totalOffset,
                radiusPx,
                radiusPx,
                paint
            )

            // Double style with rounded corners
            if (config.style == OutlineStyle.DOUBLE && widthPx >= 3f) {
                val outerOffset = totalOffset + widthPx * 2 / 3
                val outerRadius = radiusPx + widthPx * 2 / 3
                paint.strokeWidth = widthPx / 3
                paint.pathEffect = null
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    -outerOffset,
                    -outerOffset,
                    size.width + outerOffset,
                    size.height + outerOffset,
                    outerRadius,
                    outerRadius,
                    paint
                )
            }
        }
    }
}
