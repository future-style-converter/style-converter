package com.styleconverter.test.style.borders.sides

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.types.ValueExtractors.LineStyle

/**
 * Applies CSS border-*-style/width/color to a Compose Modifier.
 *
 * Compose's built-in `Modifier.border` only supports uniform solid borders
 * with a single color. To match CSS semantics we draw non-solid and
 * non-uniform borders by hand with drawBehind.
 *
 * All 10 CSS border-style keywords are rendered. The implementation
 * fidelity per keyword:
 *   - none / hidden        — not drawn (width collapses to 0 in CSS; we
 *                            just skip painting).
 *   - solid                — single stroked line, Compose fast path when
 *                            the whole border is uniform.
 *   - dashed / dotted      — PathEffect.dashPathEffect with per-style gap
 *                            ratios; cap=Round for dotted so the dashes
 *                            render as circles.
 *   - double               — two parallel 1/3-width lines with a 1/3-width
 *                            gap. Needs width >= 3px to be visible.
 *   - groove / ridge       — CSS "3D" strokes: two half-width lines with
 *                            lighter and darker shades of the declared
 *                            color. groove = dark-outer/light-inner,
 *                            ridge = light-outer/dark-inner (reversed).
 *   - inset / outset       — Uniform half-shade: inset darkens top/left
 *                            and lightens bottom/right (sunken look);
 *                            outset is the mirror (raised look). This is
 *                            the canonical CSS 3D-border shading.
 *
 * The 3D styles use a simple lighten/darken factor on the declared color
 * rather than a palette lookup — matches what every browser engine does
 * and avoids a color theory dependency in the runtime.
 */
object BorderSideApplier {

    /**
     * Apply border styling based on the configuration.
     *
     * Fast path: single Modifier.border() call when the entire border is
     * uniform + solid. Slow path: one drawBehind pass that strokes each
     * side independently.
     */
    fun applyBorders(modifier: Modifier, config: AllBordersConfig): Modifier {
        if (!config.hasBorders) return modifier

        // Fast path: every side identical + SOLID — delegates to Compose's
        // highly optimized native Modifier.border.
        val topStyle = config.top.style ?: LineStyle.SOLID
        if (config.isUniform && config.top.hasBorder && topStyle == LineStyle.SOLID) {
            val width = config.top.width ?: 1.dp
            val color = config.top.color ?: Color.Black
            return modifier.border(width, color)
        }

        // Slow path: draw each side's stroke with its own style/width/color.
        // We use `drawWithContent` (not `drawBehind`) so the border strokes
        // paint AFTER the element's content. Compose modifier chain order
        // is outer-first (left modifier runs first), and the StyleApplier
        // chain places this `borders` modifier BEFORE the `colors` one —
        // which means a `drawBehind` here would draw the borders, then the
        // background-color modifier downstream would paint OVER them and
        // hide the strokes entirely. That was the root cause for
        // `Style_Double_3px`, `Style_Groove_Thick`, `Style_Inset_Thick`,
        // and the per-side mixed audit fixtures all showing as borderless
        // boxes despite the IR carrying valid border-width / border-style
        // values. Painting after `drawContent()` puts the border on top
        // of the background and lets it survive the cascade.
        return modifier.drawWithContent {
            drawContent()
            // Pull each side's width once (absent = no border on that side).
            val topWidth = config.top.width?.toPx() ?: 0f
            val endWidth = config.end.width?.toPx() ?: 0f
            val bottomWidth = config.bottom.width?.toPx() ?: 0f
            val startWidth = config.start.width?.toPx() ?: 0f

            // Each side is painted with its own sideRole so groove/ridge/
            // inset/outset can shade top/bottom differently from left/right.
            paintSide(
                side = Side.TOP, width = topWidth,
                style = config.top.style, color = config.top.color
            )
            paintSide(
                side = Side.END, width = endWidth,
                style = config.end.style, color = config.end.color
            )
            paintSide(
                side = Side.BOTTOM, width = bottomWidth,
                style = config.bottom.style, color = config.bottom.color
            )
            paintSide(
                side = Side.START, width = startWidth,
                style = config.start.style, color = config.start.color
            )
        }
    }

    // Internal enum identifying which side is currently being painted —
    // drives the 3D-shading decision for inset/outset/groove/ridge.
    private enum class Side { TOP, END, BOTTOM, START }

    /**
     * Paint one side of the border inside a DrawScope. Dispatches to the
     * right geometry helper based on [style]; SOLID/DASHED/DOTTED use a
     * single stroked line, DOUBLE/GROOVE/RIDGE use a two-pass paint, and
     * INSET/OUTSET use a single line with a shaded color picked by [side].
     */
    private fun DrawScope.paintSide(
        side: Side,
        width: Float,
        style: LineStyle?,
        color: Color?
    ) {
        // No border on this side — either missing width or explicitly NONE/HIDDEN.
        if (width <= 0f) return
        val s = style ?: LineStyle.SOLID
        if (s == LineStyle.NONE || s == LineStyle.HIDDEN) return
        val c = color ?: Color.Black

        // Geometry: a stroked line centered on the inside of the element
        // edge, so the full stroke width sits inside the bounding box.
        val (start, end, isHorizontal) = sideGeometry(side, width)

        when (s) {
            LineStyle.SOLID -> drawStrokedLine(c, start, end, width, pathEffect = null)
            LineStyle.DASHED ->
                drawStrokedLine(
                    c, start, end, width,
                    // Dash ratio 6:4 (on:off). The prior 3:2 produced
                    // ~22 dashes on a 218px edge while Chromium renders
                    // ~9 — much longer dashes per side. 6w:4w (12px on,
                    // 8px off at w=2) lands the dash count and stroke
                    // ratio in Chromium's range so iOS+Android+web all
                    // converge on dashed-border fixtures.
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 6, width * 4))
                )
            LineStyle.DOTTED ->
                drawStrokedLine(
                    c, start, end, width,
                    // Square 1:1 dash + round caps → renders as circles.
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(width, width)),
                    cap = StrokeCap.Round
                )
            LineStyle.DOUBLE -> drawDouble(c, side, width)
            LineStyle.GROOVE -> drawGrooveOrRidge(c, side, width, groove = true)
            LineStyle.RIDGE -> drawGrooveOrRidge(c, side, width, groove = false)
            // CSS inset: top/left darker (sunken), bottom/right lighter. The
            // logical sides (START==left, END==right) mirror this.
            LineStyle.INSET ->
                drawStrokedLine(
                    shade(c, lighten = side == Side.BOTTOM || side == Side.END),
                    start, end, width, pathEffect = null
                )
            // CSS outset is the inverse of inset: top/left lighter (raised).
            LineStyle.OUTSET ->
                drawStrokedLine(
                    shade(c, lighten = side == Side.TOP || side == Side.START),
                    start, end, width, pathEffect = null
                )
            LineStyle.NONE, LineStyle.HIDDEN -> Unit // Already early-returned.
        }
    }

    /**
     * Line center + orientation for a given side, with the stroke centered
     * [width/2] inside the element edge so the full stroke is visible.
     */
    private fun DrawScope.sideGeometry(
        side: Side, width: Float
    ): Triple<Offset, Offset, Boolean> = when (side) {
        Side.TOP -> Triple(Offset(0f, width / 2), Offset(size.width, width / 2), true)
        Side.END -> Triple(
            Offset(size.width - width / 2, 0f),
            Offset(size.width - width / 2, size.height),
            false
        )
        Side.BOTTOM -> Triple(
            Offset(0f, size.height - width / 2),
            Offset(size.width, size.height - width / 2),
            true
        )
        Side.START -> Triple(Offset(width / 2, 0f), Offset(width / 2, size.height), false)
    }

    /**
     * Stroke a single line on the border edge. Thin wrapper for the common
     * case; cap defaults to Butt so SOLID edges meet cleanly at corners.
     */
    private fun DrawScope.drawStrokedLine(
        color: Color,
        start: Offset,
        end: Offset,
        width: Float,
        pathEffect: PathEffect?,
        cap: StrokeCap = StrokeCap.Butt
    ) {
        drawLine(
            color = color, start = start, end = end,
            strokeWidth = width, pathEffect = pathEffect, cap = cap
        )
    }

    /**
     * Render CSS `border-style: double` — two parallel 1/3-width lines with
     * a 1/3-width gap. Degrades to single SOLID when total width < 3px
     * because the middle gap would fall below 1px and look fuzzy.
     */
    private fun DrawScope.drawDouble(color: Color, side: Side, width: Float) {
        if (width < 3f) {
            val (s, e, _) = sideGeometry(side, width)
            drawStrokedLine(color, s, e, width, null); return
        }
        val line = width / 3f
        // Two parallel strokes; the "inner" one is pushed 2/3 of the total
        // width from the outer edge so the gap is centered.
        val offsets = listOf(line / 2f, width - line / 2f)
        for (o in offsets) {
            val (s, e) = doubleGeom(side, o)
            drawStrokedLine(color, s, e, line, null)
        }
    }

    /** Geometry for one of the two strokes in a DOUBLE border. [inset] is
     *  the distance of the stroke center from the outer edge of the box. */
    private fun DrawScope.doubleGeom(side: Side, inset: Float): Pair<Offset, Offset> = when (side) {
        Side.TOP -> Offset(0f, inset) to Offset(size.width, inset)
        Side.BOTTOM -> Offset(0f, size.height - inset) to Offset(size.width, size.height - inset)
        Side.START -> Offset(inset, 0f) to Offset(inset, size.height)
        Side.END -> Offset(size.width - inset, 0f) to Offset(size.width - inset, size.height)
    }

    /**
     * Render CSS `border-style: groove | ridge`. Groove looks like the
     * border is carved into the surface (dark outer, light inner); ridge
     * is the inverse (light outer, dark inner). Implemented as two
     * half-width adjacent lines with shaded color.
     */
    private fun DrawScope.drawGrooveOrRidge(
        color: Color, side: Side, width: Float, groove: Boolean
    ) {
        if (width < 2f) {
            val (s, e, _) = sideGeometry(side, width)
            drawStrokedLine(color, s, e, width, null); return
        }
        val half = width / 2f
        val outerShade = shade(color, lighten = !groove) // groove → dark outer
        val innerShade = shade(color, lighten = groove)  // ridge → dark inner
        val (outerStart, outerEnd) = doubleGeom(side, half / 2f)
        val (innerStart, innerEnd) = doubleGeom(side, half / 2f + half)
        drawStrokedLine(outerShade, outerStart, outerEnd, half, null)
        drawStrokedLine(innerShade, innerStart, innerEnd, half, null)
    }

    /**
     * Produce a lighter or darker shade of [base] for the 3D border styles.
     * Factor 0.5 is the usual CSS UA default for inset/outset/groove/ridge —
     * it matches Chrome and Firefox closely enough for pixel-diffs ≥ 0.95.
     */
    private fun shade(base: Color, lighten: Boolean): Color {
        // Mix toward white when lightening, toward black when darkening.
        val t = 0.5f
        val target = if (lighten) 1f else 0f
        return Color(
            red = base.red + (target - base.red) * t,
            green = base.green + (target - base.green) * t,
            blue = base.blue + (target - base.blue) * t,
            alpha = base.alpha
        )
    }
}
