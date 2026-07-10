package com.styleconverter.runtime.borders.outline

import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * Applies CSS outline styling to Compose modifiers.
 *
 * CSS (css-ui-4 §4): the outline is drawn AROUND the outline rect — the
 * border box inflated by `outline-offset` (negative offset pulls the ring
 * inside the box) — and, critically, it is painted ON TOP of the element's
 * own background and border (outline participates above the background in
 * the painting order). The previous implementation used `drawBehind`, which
 * put the ring UNDER the background painter that ColorApplier chains later:
 * any inset portion of the ring (negative offset, e.g. Borders_C14's
 * `outline-offset: -4px`) was completely covered by the background fill and
 * the outline vanished (Android-web 0.8025). `drawWithContent` +
 * paint-after-drawContent puts the ring above everything this node draws.
 *
 * 3D styles (ridge/groove/inset/outset) use two shades of the declared
 * color, split per side exactly as Chromium rasterizes them (verified by
 * pixel-scanning the Borders_C14 web capture):
 *   - light shade = the declared color UNCHANGED,
 *   - dark shade  = declared color × 0.618 per RGB channel (web paints
 *     crimson rgb(220,20,60) → rgb(136,12,37); 136/220 = 0.618).
 *   - ridge: outer half light on top/left, dark on right/bottom; inner
 *     half mirrored. groove is the exact inverse.
 *   - the light half takes ceil(w/2), the dark half floor(w/2) — the web
 *     capture shows 3px light + 2px dark for `thick` (5px).
 */
object OutlineApplier {

    /** Chromium's dark shade for 3D outline styles — see class KDoc. */
    private const val DARK_FACTOR = 0.618f

    /**
     * Apply outline to modifier. The ring paints after drawContent so it
     * sits above the background/border exactly like the browser.
     */
    fun applyOutline(modifier: Modifier, config: OutlineConfig): Modifier {
        if (!config.hasOutline) return modifier

        return modifier.drawWithContent {
            // Element content (background, borders, text) first — the CSS
            // painting order puts the outline above all of it.
            drawContent()

            when (config.style) {
                // 3D styles need per-side shading → four-trapezoid painter.
                OutlineStyle.RIDGE -> drawShadedRing(config, groove = false)
                OutlineStyle.GROOVE -> drawShadedRing(config, groove = true)
                OutlineStyle.INSET -> drawFlatShadedRing(config, inset = true)
                OutlineStyle.OUTSET -> drawFlatShadedRing(config, inset = false)
                // Uniform-color styles keep the single-stroke painter.
                else -> drawStrokeRing(config)
            }
        }
    }

    /**
     * Uniform-color ring (solid / dashed / dotted / double) drawn as a
     * native stroke. Stroke center sits at offset + width/2 outside the
     * border box, so the band covers [offset, offset+width] — matching the
     * css-ui-4 geometry (band grows OUTWARD from the inflated outline rect).
     */
    private fun DrawScope.drawStrokeRing(config: OutlineConfig) {
        val widthPx = config.width.toPx()
        val offsetPx = config.offset.toPx()
        val totalOffset = widthPx / 2 + offsetPx

        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = widthPx
            color = config.color.toArgb()
            isAntiAlias = true
            // Dash patterns follow the border-side painter's ratios.
            pathEffect = when (config.style) {
                OutlineStyle.DASHED -> DashPathEffect(floatArrayOf(widthPx * 3, widthPx * 2), 0f)
                OutlineStyle.DOTTED -> DashPathEffect(floatArrayOf(widthPx, widthPx), 0f)
                else -> null
            }
        }

        drawContext.canvas.nativeCanvas.drawRect(
            -totalOffset, -totalOffset,
            size.width + totalOffset, size.height + totalOffset,
            paint
        )

        // Double = two 1/3-width lines; the second sits further out.
        if (config.style == OutlineStyle.DOUBLE && widthPx >= 3f) {
            val outerOffset = totalOffset + widthPx * 2 / 3
            paint.strokeWidth = widthPx / 3
            paint.pathEffect = null
            drawContext.canvas.nativeCanvas.drawRect(
                -outerOffset, -outerOffset,
                size.width + outerOffset, size.height + outerOffset,
                paint
            )
        }
    }

    /**
     * Ridge/groove ring: two concentric per-side-shaded bands. The light
     * band is ceil(w/2) thick, the dark band the remainder — the split
     * Chromium rasterizes for `thick` (3px + 2px).
     */
    private fun DrawScope.drawShadedRing(config: OutlineConfig, groove: Boolean) {
        val w = config.width.toPx()
        val off = config.offset.toPx()
        val light = config.color
        val dark = darken(config.color)
        // Outer half thickness (light for ridge top/left) = ceil(w/2).
        val outerW = kotlin.math.ceil(w / 2f)
        val innerW = w - outerW

        // For RIDGE: outer band light on top/left, dark on right/bottom;
        // inner band is mirrored. GROOVE swaps both.
        val outerTL = if (groove) dark else light
        val outerBR = if (groove) light else dark
        val innerTL = if (groove) light else dark
        val innerBR = if (groove) dark else light

        // Outer band spans [off+innerW, off+w] outside the border box,
        // inner band [off, off+innerW].
        drawSideBands(distOut = off + w, thickness = outerW, tl = outerTL, br = outerBR)
        if (innerW > 0f) {
            drawSideBands(distOut = off + innerW, thickness = innerW, tl = innerTL, br = innerBR)
        }
    }

    /**
     * Inset/outset ring: a single band whose top/left and right/bottom
     * halves take opposite shades (inset = dark top/left, sunken look).
     */
    private fun DrawScope.drawFlatShadedRing(config: OutlineConfig, inset: Boolean) {
        val w = config.width.toPx()
        val off = config.offset.toPx()
        val light = config.color
        val dark = darken(config.color)
        drawSideBands(
            distOut = off + w, thickness = w,
            tl = if (inset) dark else light,
            br = if (inset) light else dark
        )
    }

    /**
     * Paint one ring layer as four mitered trapezoids. [distOut] is the
     * distance from the border box edge to the layer's OUTER boundary;
     * [thickness] the layer's band width; [tl]/[br] the colors for the
     * top+left and bottom+right sides. Trapezoids meet on the 45° corner
     * diagonals, matching the browser's mitered side joins.
     */
    private fun DrawScope.drawSideBands(
        distOut: Float,
        thickness: Float,
        tl: Color,
        br: Color
    ) {
        if (thickness <= 0f) return
        val w = size.width
        val h = size.height
        // Outer rect corners (border box inflated by distOut).
        val oL = -distOut; val oT = -distOut; val oR = w + distOut; val oB = h + distOut
        // Inner rect corners (one band-thickness inside the outer rect).
        val iL = oL + thickness; val iT = oT + thickness
        val iR = oR - thickness; val iB = oB - thickness

        // Each side is the quad between the outer and inner rect edges;
        // shared corners land on the 45° diagonal automatically because
        // both rects shrink by the same thickness in x and y.
        fun quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) {
            val p = Path().apply {
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
            }
            drawPath(p, color)
        }
        // Top: outer TL → outer TR → inner TR → inner TL.
        quad(Offset(oL, oT), Offset(oR, oT), Offset(iR, iT), Offset(iL, iT), tl)
        // Left: outer TL → inner TL → inner BL → outer BL.
        quad(Offset(oL, oT), Offset(iL, iT), Offset(iL, iB), Offset(oL, oB), tl)
        // Bottom: outer BL → inner BL → inner BR → outer BR.
        quad(Offset(oL, oB), Offset(iL, iB), Offset(iR, iB), Offset(oR, oB), br)
        // Right: outer TR → outer BR → inner BR → inner TR.
        quad(Offset(oR, oT), Offset(oR, oB), Offset(iR, iB), Offset(iR, iT), br)
    }

    /** Dark shade for the 3D styles — DARK_FACTOR per channel, alpha kept. */
    internal fun darken(base: Color): Color = Color(
        red = base.red * DARK_FACTOR,
        green = base.green * DARK_FACTOR,
        blue = base.blue * DARK_FACTOR,
        alpha = base.alpha
    )
}
