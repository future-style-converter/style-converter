package com.styleconverter.runtime.borders.sides

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
import com.styleconverter.runtime.core.types.ValueExtractors.LineStyle

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
 *   - groove / ridge       — CSS "3D" strokes: two half-width adjacent
 *                            bands. Blink composes them as two half-
 *                            borders styled inset(outer)/outset(inner)
 *                            for groove (swapped for ridge), and EACH
 *                            half then follows the per-side darkening
 *                            rule below — so groove is dark-outer/light-
 *                            inner on top/left but LIGHT-outer/dark-
 *                            inner on bottom/right (ridge mirrored).
 *   - inset / outset       — Per-side band shade: inset darkens top/left
 *                            and keeps bottom/right at the declared color
 *                            (sunken look); outset is the mirror (raised
 *                            look). This is the canonical CSS 3D shading.
 *
 * The 3D styles use Chromium's two-tone palette (see shade()): the dark
 * band follows Blink Color::Dark()'s subtractive model, and the light band
 * is the declared color UNCHANGED unless it would not read against that
 * dark band — Blink's contrast-ratio gate (lightBandLifts) then lifts it
 * via Color::Light(). 3D painting is UA-defined, so the web reference
 * engine's own arithmetic is the cross-platform contract. The Swift twin
 * is `StyleEngine/borders/sides/BlinkBorderShade.swift` (landed in the
 * 2026-09 retro by R6's seam patch 03; `BorderSideApplier.swift`
 * `shade(_:light:)` delegates to it): the same helpers with byte-parallel
 * bodies, one shared pinned table (BorderShadeBlinkGateTest /
 * BordersTests.testLightBandLiftsBlinkGate) and the same 8-bit input rule
 * (BlinkBorderShade.pack8 — its class banner states the contract). Retro
 * P2e's "no such Swift file / lifts pure black only" wording described the
 * tree BEFORE that seam applied; round-2 F1 re-trued it (skeptics S3, S6).
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

        // Fast path: every side identical + DECLARED SOLID — delegates to
        // Compose's highly optimized native Modifier.border. No `?: SOLID`
        // default: `hasBorder` already requires a declared visible style
        // (CSS 2.1 §8.5.3 — absent = `none` = used width 0), same gate as
        // paintSide below (retro R4 / A11#5).
        if (config.isUniform && config.top.hasBorder && config.top.style == LineStyle.SOLID) {
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

    // Enum identifying which side is currently being painted — drives
    // the 3D-shading decision for inset/outset/groove/ridge. Internal
    // (not private) so JVM tests can pin the per-side band order.
    internal enum class Side { TOP, END, BOTTOM, START }

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
        // No border on this side — missing width, or the style is
        // none/hidden or ABSENT. CSS 2.1 §8.5.3: border-style initial is
        // `none`, and none/absent zeroes the used width — a width-only
        // longhand (`border-inline-end-width: 6px`) must paint NOTHING,
        // matching the web reference. The old `style ?: SOLID` default
        // painted style-less sides as solid currentColor bands.
        if (width <= 0f) return
        val s = style ?: return
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
                    // Per-edge FITTED intervals — Chromium fits an integer
                    // dash count to each edge so it starts AND ends on a
                    // full dash; a fixed rhythm truncated the final dash
                    // mid-way at the corners. Sides are axis-aligned, so
                    // |Δx| + |Δy| is the exact edge length.
                    pathEffect = PathEffect.dashPathEffect(
                        fittedDashIntervals(
                            kotlin.math.abs(end.x - start.x) +
                                kotlin.math.abs(end.y - start.y),
                            width
                        )
                    )
                )
            LineStyle.DOTTED ->
                // True spaced circles. The previous 1:1 dash + Round cap
                // did NOT render dots: a Round cap extends each dash by
                // width/2 on BOTH ends, so every "dot" was a 2w-long
                // oblong with only a w gap — adjacent blobs visually
                // merged (Borders_C01/C04 web-Android 0.87-0.88).
                // Chromium paints `dotted` as circles of diameter w with
                // ≈w of clear space between them; drawDotted mirrors that.
                drawDotted(c, start, end, width)
            LineStyle.DOUBLE -> drawDouble(c, side, width)
            LineStyle.GROOVE -> drawGrooveOrRidge(c, side, width, groove = true)
            LineStyle.RIDGE -> drawGrooveOrRidge(c, side, width, groove = false)
            // CSS inset: top/left dark band (sunken), bottom/right the
            // declared color (Chromium's light band == base — see shade()).
            // The logical sides (START==left, END==right) mirror this;
            // the shared rule lives in isLightBand so groove/ridge reuse it.
            LineStyle.INSET ->
                drawStrokedLine(
                    shade(c, lighten = isLightBand(side, inset = true)),
                    start, end, width, pathEffect = null
                )
            // CSS outset is the inverse of inset: top/left keep the
            // declared color (raised), bottom/right take the dark band.
            LineStyle.OUTSET ->
                drawStrokedLine(
                    shade(c, lighten = isLightBand(side, inset = false)),
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
     * Dash intervals `[on, off]` in px for CSS `border-style: dashed`.
     *
     * Dashed painting is UA-defined (css-backgrounds-3 §3.2 only says
     * "square-ended dashes"), so Chromium's painter is our cross-platform
     * reference, and its on:off rhythm is width-dependent:
     *   - Thick borders (w >= 3px): at the dashed fixture's w=5 Chromium
     *     paints ≈10px on / 5px off (~11 dashes on a 160px edge) — a
     *     2w:1w rhythm. A fixed 6w:4w made native dashes 3x too long
     *     there, so thick widths use [2w, w].
     *   - Thin borders (w < 3px): the 6w:4w tuning was measured against
     *     Chromium at w=2 (12px on / 8px off ≈ 9 dashes on a 218px edge).
     * These are the NOMINAL intervals only — the painter always runs them
     * through fittedDashIntervals() so each edge starts and ends on a
     * full dash (the fit preserves this on:off ratio). Internal (not
     * private) so JVM tests can pin the interval choice — the iOS
     * applier mirrors this helper (BorderSideApplier.swift) so both
     * natives derive intervals from one shared rule per platform.
     */
    internal fun dashedIntervals(width: Float): FloatArray =
        if (width >= 3f) floatArrayOf(width * 2, width) // thick → Chromium's ~2w:1w
        else floatArrayOf(width * 6, width * 4)         // thin → measured w=2 tuning

    /**
     * Fit the nominal dashed rhythm to one edge of length [len] so the
     * edge starts AND ends on a full dash. Chromium's dashed painter
     * adjusts the dash/gap pair per edge so an integer number of dashes
     * spans it exactly; painting the fixed nominal rhythm instead
     * truncated the final dash mid-way at three of the four corners
     * (the phase never resets, so only the starting corner lined up).
     *
     * n = max(1, round-half-up((len + gap) / (dash + gap))) full dashes
     * — the numerator adds back the ONE trailing gap the last dash does
     * not need — then both intervals scale by the single factor
     * len / (n·dash + (n-1)·gap), preserving the nominal on:off ratio
     * while making n dashes + (n-1) gaps == len exactly. Rounding is
     * floor(x+0.5), not kotlin.math.round (rint/ties-to-even), for the
     * same exact-half reason documented on dottedDotCount. Internal so
     * JVM tests can pin the fit; the iOS applier mirrors this helper
     * (BorderSideApplier.swift) so the natives share one rule.
     */
    internal fun fittedDashIntervals(len: Float, width: Float): FloatArray {
        // Nominal rhythm for this width — the ratio the fit preserves.
        val nominal = dashedIntervals(width)
        val dash = nominal[0]
        val gap = nominal[1]
        // Degenerate edge — nothing to fit against; keep the nominal.
        if (len <= 0f) return nominal
        // Integer dash count closest to the nominal rhythm (min 1: an
        // edge shorter than one nominal dash paints as a single full
        // dash, i.e. solid — same degenerate outcome as Chromium).
        val n = kotlin.math.max(
            1, kotlin.math.floor((len + gap) / (dash + gap) + 0.5f).toInt()
        )
        // One shared stretch/shrink factor keeps the on:off proportion.
        val scale = len / (n * dash + (n - 1) * gap)
        return floatArrayOf(dash * scale, gap * scale)
    }

    /**
     * Render CSS `border-style: dotted` as a row of filled circles along
     * the side's centerline. Geometry mirrors Chromium's dotted painter
     * (blink `StylePainter`): dot diameter = border width; dots spaced so
     * the clear gap between neighbours is ≈ one width. We fit an integer
     * dot count to the side length and distribute the remainder evenly so
     * the first/last dots sit flush with the corners — same visual rhythm
     * the browser produces.
     */
    private fun DrawScope.drawDotted(
        color: Color,
        start: Offset,
        end: Offset,
        width: Float
    ) {
        // Vector along the side and its length. Sides are always axis-
        // aligned but the math below works for either orientation.
        val dx = end.x - start.x
        val dy = end.y - start.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len <= 0f || width <= 0f) return
        val r = width / 2f
        // Ideal center-to-center pitch = 2×width (dot w + gap w). Fit an
        // integer count: n dots need (n-1) pitches plus one diameter.
        val count = dottedDotCount(len, width)
        if (count == 1) {
            // Side shorter than one pitch — single dot centered on the side.
            drawCircle(color, r, Offset(start.x + dx / 2f, start.y + dy / 2f))
            return
        }
        // Even spacing with first/last dot centers inset r from each end so
        // the dots stay fully inside the box (Chromium keeps corner dots
        // flush with the adjacent side's edge). Extracted to a helper (wave
        // 35, lane B8) purely so the JVM suite can pin the PITCH against the
        // measured WPT refs, not just the dot COUNT — see dottedDotStep.
        val step = dottedDotStep(len, width, count)
        val ux = dx / len
        val uy = dy / len
        for (i in 0 until count) {
            val d = r + step * i
            drawCircle(color, r, Offset(start.x + ux * d, start.y + uy * d))
        }
    }

    /**
     * Number of dots Chromium fits on a dotted side of length [len] with
     * dot diameter [width]: pitches = round-half-UP((len - w) / 2w), dots =
     * pitches + 1, first/last dot flush with the side ends.
     *
     * Rounding MUST be half-up (floor(x+0.5)), NOT kotlin.math.round:
     * kotlin.math.round is rint (ties-to-even), so the exact-half case
     * dropped a dot vs Chromium — Borders_C01 (240px side, 8px dots:
     * (240-8)/16 = 14.5) painted 15 dots on Android vs web's 16, putting
     * every dot after the first two out of phase (red rings across the
     * whole diff strip at Android-web 0.8965). Internal for JVM tests.
     */
    internal fun dottedDotCount(len: Float, width: Float): Int {
        if (len <= 0f || width <= 0f) return 0
        val pitches = kotlin.math.floor((len - width) / (2f * width) + 0.5f).toInt()
        return kotlin.math.max(1, pitches + 1)
    }

    /**
     * Center-to-center PITCH of a fitted dotted side: the span between the
     * two inset end-dot centers, `len - width`, shared evenly across the
     * `count - 1` intervals. A single-dot side has no interval → 0.
     *
     * Extracted from [drawDotted] verbatim (wave 35, lane B8) — same
     * expression, no behaviour change — so the pitch can be pinned against
     * the frozen WPT ref PNGs instead of only being asserted indirectly
     * through a raster. Measured on
     * tools/wpt/refs/9b5435e5…/white-black-ink-font-lh-imgpad-htmlpins/
     * css-align/, whose `.block { border: 5px dotted blue }` boxes give
     * four independent geometries (w = 5 throughout):
     *   len 90 (justify-self-static-position-001, 80px box + 2×5 border)
     *     → 10 dots, pitch 9.444  (ref dot lefts 67,77,86,95,105,114,124,
     *       133,143,152 — top edge; 17,27,36,45,55,64,74,83,93,102 — left)
     *   len 85 (align-self-static-position-001, width:75% of 100px)
     *     → 9 dots, pitch 10.0    (ref 67,77,87,97,107,117,127,137,147)
     *   len 60 (same test, height:50%)
     *     → 7 dots, pitch 9.167   (ref 17,26,35,45,54,63,72)
     * The len-90 case is a SECOND independent witness for the half-up
     * rounding in [dottedDotCount]: (90-5)/10 = 8.5 exactly, and Chromium
     * paints 10 dots (9 pitches), which ties-to-even would have made 9.
     * Internal for the JVM suite; the iOS twin carries dottedDotStep with
     * identical arithmetic and the same pin table.
     */
    internal fun dottedDotStep(len: Float, width: Float, count: Int): Float =
        if (count > 1) (len - width) / (count - 1).toFloat() else 0f

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
     * Render CSS `border-style: groove | ridge` as two half-width
     * adjacent lines. The band shades are PER-SIDE (see
     * grooveRidgeBandShades): the old code hardcoded dark-outer/light-
     * inner for groove on every side, which is only right on top/left —
     * on bottom/right the carved illusion needs the mirror, so all four
     * grooved sides read as lit from the CSS top-left light source.
     */
    private fun DrawScope.drawGrooveOrRidge(
        color: Color, side: Side, width: Float, groove: Boolean
    ) {
        if (width < 2f) {
            val (s, e, _) = sideGeometry(side, width)
            drawStrokedLine(color, s, e, width, null); return
        }
        val half = width / 2f
        // Outer/inner band colors from the per-side Blink rule.
        val (outerShade, innerShade) = grooveRidgeBandShades(color, side, groove)
        val (outerStart, outerEnd) = doubleGeom(side, half / 2f)
        val (innerStart, innerEnd) = doubleGeom(side, half / 2f + half)
        drawStrokedLine(outerShade, outerStart, outerEnd, half, null)
        drawStrokedLine(innerShade, innerStart, innerEnd, half, null)
    }

    /**
     * Chromium's per-side 3D darkening rule, shared by inset/outset and
     * (via grooveRidgeBandShades) groove/ridge: a band styled `inset` is
     * DARK on top/left and light on bottom/right — the sunken look under
     * the CSS top-left light source — and `outset` is the exact mirror.
     * Equivalently: dark ⇔ (side == top‖left) == (style == inset).
     * Returns true when the band on [side] takes the LIGHT shade.
     * Internal so JVM tests can pin the rule directly.
     */
    internal fun isLightBand(side: Side, inset: Boolean): Boolean =
        if (inset) side == Side.BOTTOM || side == Side.END // sunken: light escapes bottom/right
        else side == Side.TOP || side == Side.START        // raised: light hits top/left

    /**
     * Per-side (outer, inner) band colors for groove/ridge.
     *
     * Blink composes groove as two half-borders: the OUTER half styled
     * `inset` and the INNER half `outset` (the carved trench); ridge
     * swaps the styles (the raised rim). Each half then follows the
     * per-side darkening rule (isLightBand) like a real inset/outset
     * border would — so groove on TOP is dark-outer/light-inner but
     * groove on BOTTOM is light-outer/dark-inner, and ridge mirrors
     * both. Internal so JVM tests can pin all four sides' band order.
     */
    internal fun grooveRidgeBandShades(
        base: Color, side: Side, groove: Boolean
    ): Pair<Color, Color> =
        // groove → outer half is the inset-styled one; ridge → outset.
        shade(base, lighten = isLightBand(side, inset = groove)) to
            // The inner half always takes the opposite style of the outer.
            shade(base, lighten = isLightBand(side, inset = !groove))

    /**
     * Chromium's two-tone palette for the 3D border styles
     * (groove/ridge/inset/outset) — Blink Color::Dark()/Color::Light()
     * gated by box_border_painter.cc CalculateBorderStyleColor. The
     * arithmetic lives in [BlinkBorderShade] (retro R6, audit A7#2); its iOS
     * twin is `StyleEngine/borders/sides/BlinkBorderShade.swift`, which
     * `BorderSideApplier.swift` `shade(_:light:)` delegates to — byte-parallel
     * bodies, one shared pinned table, one 8-bit input rule (pack8). Retro
     * P2e (BACKLOG queue entry (f)) had rewritten this KDoc to "a Swift
     * `BlinkBorderShade.swift` that has never existed" — correct until R6's
     * seam patch 03 landed that very file in the integrated tree, stale
     * after it; round-2 F1 re-trued it (skeptics S3 defect 3, S6 defect 4).
     * This entry point stays so every caller (the side painters above,
     * OutlineApplier, the fidelity pins) keeps one name for "the border
     * palette". Alpha is untouched throughout.
     */
    internal fun shade(base: Color, lighten: Boolean): Color = BlinkBorderShade.shade(base, lighten)
}
