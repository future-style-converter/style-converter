package com.styleconverter.runtime.columns

// css-gap-decorations-1 §6 — painting the segments GapDecorationSegments
// produced (wave 24, lane GAPS-A).
//
// Stroke semantics are BORROWED, not re-invented: css-gap-decorations-1
// defines `*-rule-style` as the `<line-style>` grammar of
// css-backgrounds-3, i.e. exactly what BorderSideApplier already renders.
// The double/dotted geometry below mirrors that file line for line (and
// reuses its pinned dot-count function) so a gap rule and a border of the
// same width and style put pixels in the same places.
//
// A segment's orientation follows its family and never varies:
// GapAxis.COLUMN segments are VERTICAL bars (thickness = rect width),
// GapAxis.ROW segments are HORIZONTAL bars (thickness = rect height).
// GapDecorationSegments guarantees that for both flex-direction axes.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.borders.sides.BorderSideApplier

/** Paints gap-decoration segments into a [DrawScope]. */
object GapDecorationPainter {

    /**
     * Paint every segment, in the order given (paint order is already
     * resolved by `rule-overlap` upstream — last wins on a crossing).
     */
    fun paint(scope: DrawScope, segments: List<GapSegment>, config: GapDecorationConfig) {
        for (segment in segments) {
            val spec = config.specFor(segment.axis)
            // `currentColor` is the CSS initial for *-rule-color. This
            // runtime resolves inherited colour in a different extractor,
            // so an absent colour degrades to black — reported, never
            // silently skipped, because the rule must still occupy space.
            val color = spec.color ?: run {
                PropertyTracker.markUnhandled("GapRuleColor:currentColor"); Color.Black
            }
            paintOne(scope, segment, spec.style, color)
        }
    }

    /** Dispatch one segment on its `<line-style>`. */
    private fun paintOne(
        scope: DrawScope,
        segment: GapSegment,
        style: ColumnRuleStyle,
        color: Color
    ) {
        val r = segment.rect
        val vertical = segment.axis == GapAxis.COLUMN
        // Thickness runs across the bar, length along it.
        val thickness = if (vertical) r.width else r.height
        val length = if (vertical) r.height else r.width
        if (thickness <= 0f || length <= 0f) return
        when (style) {
            // Solid fills the whole band — WPT flex-gap-decorations-003
            // draws its 10px rules as plain 10px-wide boxes.
            ColumnRuleStyle.SOLID -> fill(scope, r, color)
            // WPT 004: `double`. Two outer thirds of the band, hollow
            // middle third — the ref renders it as `border-left: 10px
            // double`, i.e. Chromium's own border painter.
            ColumnRuleStyle.DOUBLE -> double(scope, r, color, vertical, thickness)
            // WPT 005: `dotted` — circles of diameter = the rule width.
            ColumnRuleStyle.DOTTED -> dotted(scope, r, color, vertical, thickness, length)
            // Not pinned by any css-gaps ref in this section, but part of
            // the same <line-style> grammar: dashes at the border rhythm.
            ColumnRuleStyle.DASHED -> dashed(scope, r, color, vertical, thickness, length)
            // none/hidden never reach here (GapRuleSpec.paints gates them).
            ColumnRuleStyle.NONE, ColumnRuleStyle.HIDDEN -> Unit
            // 3D styles need a two-band shade that no css-gaps ref pins
            // for rules. Paint the flat colour so the rule still occupies
            // its band, and report so the gap is visible in coverage.
            ColumnRuleStyle.GROOVE, ColumnRuleStyle.RIDGE,
            ColumnRuleStyle.INSET, ColumnRuleStyle.OUTSET -> {
                PropertyTracker.markUnhandled("GapRuleStyle3D:$style"); fill(scope, r, color)
            }
        }
    }

    /** Fill a whole segment rectangle. */
    private fun fill(scope: DrawScope, r: GapRect, color: Color) {
        scope.drawRect(color, Offset(r.left, r.top), Size(r.width, r.height))
    }

    /**
     * `double`: two bands of one third the thickness at the two outer
     * edges. Degrades to solid below 3px for the same reason
     * BorderSideApplier.drawDouble does — the middle gap would be
     * sub-pixel and read as a fuzzy smear.
     */
    private fun double(
        scope: DrawScope,
        r: GapRect,
        color: Color,
        vertical: Boolean,
        thickness: Float
    ) {
        if (thickness < 3f) { fill(scope, r, color); return }
        val band = thickness / 3f
        if (vertical) {
            scope.drawRect(color, Offset(r.left, r.top), Size(band, r.height))
            scope.drawRect(color, Offset(r.right - band, r.top), Size(band, r.height))
        } else {
            scope.drawRect(color, Offset(r.left, r.top), Size(r.width, band))
            scope.drawRect(color, Offset(r.left, r.bottom - band), Size(r.width, band))
        }
    }

    /**
     * `dotted`: circles of diameter = [thickness], first and last flush
     * with the segment ends, count from the pinned Chromium fit in
     * BorderSideApplier.dottedDotCount (half-UP rounding — see its doc).
     */
    private fun dotted(
        scope: DrawScope,
        r: GapRect,
        color: Color,
        vertical: Boolean,
        thickness: Float,
        length: Float
    ) {
        val radius = thickness / 2f
        val count = BorderSideApplier.dottedDotCount(length, thickness)
        if (count <= 0) return
        // Centre line of the bar; dots march along it.
        val axisFixed = if (vertical) r.left + radius else r.top + radius
        val axisStart = if (vertical) r.top else r.left
        if (count == 1) {
            val mid = axisStart + length / 2f
            scope.drawCircle(color, radius, center(vertical, axisFixed, mid)); return
        }
        val step = (length - thickness) / (count - 1).toFloat()
        for (i in 0 until count) {
            val d = axisStart + radius + step * i
            scope.drawCircle(color, radius, center(vertical, axisFixed, d))
        }
    }

    /**
     * `dashed`: alternating filled runs at the border rhythm (dash and
     * gap both ≈ 2× the thickness, an integer count fitted to the
     * segment so it starts and ends on a full dash — the same fitting
     * BorderSideApplier applies per edge).
     */
    private fun dashed(
        scope: DrawScope,
        r: GapRect,
        color: Color,
        vertical: Boolean,
        thickness: Float,
        length: Float
    ) {
        // n dashes leave n-1 equal gaps, so the segment holds (2n-1) units
        // of one dash length d: length = (2n-1)·d. Pick n from the nominal
        // 2×thickness rhythm, then solve d exactly so the last dash ends
        // flush with the segment (no half dash at the far end).
        val nominal = thickness * 2f
        val n = kotlin.math.max(1, kotlin.math.round((length / nominal + 1f) / 2f).toInt())
        val d = length / (2 * n - 1).toFloat()
        val axisStart = if (vertical) r.top else r.left
        for (i in 0 until n) {
            val start = axisStart + 2f * d * i
            if (vertical) scope.drawRect(color, Offset(r.left, start), Size(thickness, d))
            else scope.drawRect(color, Offset(start, r.top), Size(d, thickness))
        }
    }

    /** Build a dot centre from the fixed cross coordinate and the along-axis one. */
    private fun center(vertical: Boolean, fixed: Float, along: Float): Offset =
        if (vertical) Offset(fixed, along) else Offset(along, fixed)
}
