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

    /**
     * Apply outline to modifier. The ring paints after drawContent so it
     * sits above the background/border exactly like the browser.
     *
     * Outline-vs-clip ordering (wave-18 cleanup, clip-003 audit): this
     * drawWithContent is chained at StyleApplier step 5, OUTER to the
     * step-7 overflow clip (Compose modifiers wrap left-to-right, and the
     * clip's graphicsLayer only bounds draws INNER to it) — so the ring
     * already escapes the element's OWN overflow clip, matching css-ui-4
     * §4 (the element's clip cuts its content, never its outline; the
     * clip-003 ref keeps the red ring on content-clipping squares). The
     * iOS twin needed an explicit hoist (StyleBuilder.hoistedOutline)
     * because SwiftUI chains wrap the other way. TODO (both natives,
     * lane-3): an ANCESTOR scroll/clip container SHOULD clip a
     * descendant's outline ink — that propagation isn't wired yet.
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
     * Ridge/groove OUTLINE ring. Calibrated pixel-for-pixel against a
     * fresh headless-Chrome probe (wave 5, scratchpad/outline-probe.png,
     * all four sides scanned OUTWARD from the box edge):
     *
     *   `3px groove #eee`:   top/left  → light 1px in, DARK 2px out
     *                        bottom/right → DARK 2px in, light 1px out
     *   `5px ridge crimson`: top/left  → dark 2px in, LIGHT 3px out
     *                        bottom/right → LIGHT 3px in, dark 2px out
     *
     * Unified rule: the ring splits into a CEIL(w/2) band and a FLOOR
     * band; the ceil band is the DARK shade for groove and the LIGHT
     * (declared) color for ridge, and it sits at the ring's OUTER half on
     * the top/left sides but the INNER half on the bottom/right sides —
     * the classic top-left "light source" bevel. (The pre-wave-5 painter
     * put ceil() at the outer half on ALL sides and also swapped the
     * light/dark assignment per side, matching no Chrome pixel — it held
     * PW_Borders_Sizing_04/_02 at 0.66-0.94 across waves.)
     */
    private fun DrawScope.drawShadedRing(config: OutlineConfig, groove: Boolean) {
        val w = config.width.toPx()
        val off = config.offset.toPx()
        val light = config.color
        val dark = darken(config.color)
        // Chrome's split: ceil band + floor band.
        val tCeil = kotlin.math.ceil(w / 2f)
        val tFloor = w - tCeil
        val ceilColor = if (groove) dark else light
        val floorColor = if (groove) light else dark
        // Top/left: ceil band at the ring's OUTER half.
        halfRing(distOut = off + w, thickness = tCeil, color = ceilColor, topLeft = true)
        if (tFloor > 0f) {
            halfRing(distOut = off + tFloor, thickness = tFloor, color = floorColor, topLeft = true)
        }
        // Bottom/right: ceil band at the ring's INNER half.
        if (tFloor > 0f) {
            halfRing(distOut = off + w, thickness = tFloor, color = floorColor, topLeft = false)
        }
        halfRing(distOut = off + tCeil, thickness = tCeil, color = ceilColor, topLeft = false)
    }

    /**
     * Paint the top+left (or bottom+right) trapezoids of one ring layer.
     * [distOut] is the distance from the border box edge to the layer's
     * OUTER boundary, [thickness] its band width. Sides meet on the 45°
     * corner diagonals of THIS layer's rects (mitered joins); adjacent
     * layers with different radial positions overlap corners by ≤1px,
     * which is inside Chrome's own corner rasterization noise.
     */
    private fun DrawScope.halfRing(
        distOut: Float,
        thickness: Float,
        color: Color,
        topLeft: Boolean
    ) {
        if (thickness <= 0f) return
        val w = size.width
        val h = size.height
        // Outer rect corners (border box inflated by distOut).
        val oL = -distOut; val oT = -distOut; val oR = w + distOut; val oB = h + distOut
        // Inner rect corners (one band-thickness inside the outer rect).
        val iL = oL + thickness; val iT = oT + thickness
        val iR = oR - thickness; val iB = oB - thickness
        fun quad(a: Offset, b: Offset, c: Offset, d: Offset) {
            val p = Path().apply {
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
            }
            drawPath(p, color)
        }
        if (topLeft) {
            // Top: outer TL → outer TR → inner TR → inner TL.
            quad(Offset(oL, oT), Offset(oR, oT), Offset(iR, iT), Offset(iL, iT))
            // Left: outer TL → inner TL → inner BL → outer BL.
            quad(Offset(oL, oT), Offset(iL, iT), Offset(iL, iB), Offset(oL, oB))
        } else {
            // Bottom: outer BL → inner BL → inner BR → outer BR.
            quad(Offset(oL, oB), Offset(iL, iB), Offset(iR, iB), Offset(oR, oB))
            // Right: outer TR → outer BR → inner BR → inner TR.
            quad(Offset(oR, oT), Offset(oR, oB), Offset(iR, iB), Offset(iR, iT))
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

    /**
     * Dark shade for the 3D styles — Chromium's Color::Dark(), i.e. an HSL
     * transform L' = max(0, L·2/3 − 0.02) with hue/saturation preserved.
     * Calibrated against the wave-5 headless-Chrome probe:
     *   crimson rgb(220,20,60)  → rgb(136,12,37)   (this formula: 137,12,37)
     *   #eee    rgb(238,238,238)→ rgb(154,154,154) (this formula: 154,154,154)
     * The previous flat per-channel ×0.618 was exact for crimson but 7/255
     * off for grays — visible on every currentColor(#eee) 3D outline.
     */
    internal fun darken(base: Color): Color {
        val r = base.red; val g = base.green; val b = base.blue
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        val l = (mx + mn) / 2f
        val c = mx - mn
        // Hue in [0,6) sextants; saturation per HSL.
        val h = when {
            c == 0f -> 0f
            mx == r -> ((g - b) / c).mod(6f)
            mx == g -> (b - r) / c + 2f
            else -> (r - g) / c + 4f
        }
        val s = if (c == 0f) 0f else c / (1f - kotlin.math.abs(2f * l - 1f))
        val l2 = (l * 2f / 3f - 0.02f).coerceAtLeast(0f)
        // HSL → RGB at the darkened lightness.
        val c2 = (1f - kotlin.math.abs(2f * l2 - 1f)) * s
        val x2 = c2 * (1f - kotlin.math.abs(h.mod(2f) - 1f))
        val m = l2 - c2 / 2f
        val (r2, g2, b2) = when {
            h < 1f -> Triple(c2, x2, 0f)
            h < 2f -> Triple(x2, c2, 0f)
            h < 3f -> Triple(0f, c2, x2)
            h < 4f -> Triple(0f, x2, c2)
            h < 5f -> Triple(x2, 0f, c2)
            else -> Triple(c2, 0f, x2)
        }
        return Color(
            red = (r2 + m).coerceIn(0f, 1f),
            green = (g2 + m).coerceIn(0f, 1f),
            blue = (b2 + m).coerceIn(0f, 1f),
            alpha = base.alpha
        )
    }
}
