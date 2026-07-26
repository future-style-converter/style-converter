package com.styleconverter.runtime.layout

// FloatRowLayout — the Compose adapter over the pure FloatRowPacking
// twins (wave-19 lane FLOAT). Renders ONE float run (a ≥2 streak of
// left-floating block siblings, FloatRowPacking.segment) side-by-side
// per CSS 2.1 §9.5, inside the parent's block Column. Composed-WPT
// capture only — the caller (ComponentRenderer's block child loop)
// gates on LocalWptCaptureMode, so the dark-stage 327 corpus never
// enters this layout.

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Lay a float run out horizontally. Each child is measured UNBOUNDED
 * (P7 — the absposOverflowMeasure discipline, css-position-3 §2.1 /
 * CSS 2.1 §10.3.5): a float's own size chain must resolve its SPECIFIED
 * size even inside a narrow shrink-to-fit abspos ancestor (the
 * descendant-static-position-001 grey float was clamped away by the
 * ~20px incoming constraint before this lane). The run then REPORTS a
 * constraint-fitting size (same coercion contract as absposReportedAxis)
 * and places the full-size children unclipped — Compose does not clip
 * children by default, so overflowing float ink draws exactly like CSS
 * overflow:visible in the browser-ref.
 *
 * [strutted] marks a run terminated by a `<br clear>` sibling (P2): its
 * reported height is floored at the composed-WPT line-box pin
 * (FloatRowPacking.STRUT_PX, P6) so consecutive rows advance like the
 * ref's empty br line boxes (20px pitch on the justify-self-001 rows).
 *
 * [rightPacked] (wave-20 W3, P12/P13) selects the mirrored §9.5.1
 * packing for `float: right` runs: members pack right-to-left via
 * FloatRowPacking.layoutEnd, and the run box anchors its RIGHT edge at
 * the containing block's right edge — when the incoming max width is
 * finite the run reports THAT width and right-aligns its members inside
 * it; unbounded (the abspos shrink-to-fit measure) reports the bare run
 * extent, whose right edge then coincides with the containing block's
 * (descendant-static-position-002/004: grey|green side by side).
 */
@Composable
internal fun FloatRowLayout(
    strutted: Boolean,
    rightPacked: Boolean = false,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier) { measurables, constraints ->
        // P7 — unbounded measure (min 0 / max ∞ both axes): each float's
        // own width/height modifiers decide its margin-box size (margins
        // are absolutePadding in this engine, so measured size == the
        // §9.5 outer box the packer needs).
        val placeables = measurables.map { it.measure(Constraints()) }
        // P6 — the strut arrives in px: the pin is 20 CSS px == 20dp at
        // the capture density (density-agnostic pure math takes px).
        val strutPx = if (strutted) FloatRowPacking.STRUT_PX.dp.toPx().toDouble() else 0.0
        // P3/P4 — pure greedy packing at UNBOUNDED available width (see
        // FloatRowPacking.layout's doc for why the synthetic IR frame
        // width must not drive the wrap for the WPT corpus). Right runs
        // take the mirrored plan (P12) — shared rows, reflected x.
        val widths = placeables.map { it.width.toDouble() }
        val heights = placeables.map { it.height.toDouble() }
        val plan =
            if (rightPacked) FloatRowPacking.layoutEnd(widths, heights, Double.POSITIVE_INFINITY, strutPx)
            else FloatRowPacking.layout(widths, heights, Double.POSITIVE_INFINITY, strutPx)
        // Report a size the parent block flow can live with (the exact
        // absposReportedAxis coercion): the flow geometry of following
        // siblings stays inside the incoming envelope while the drawn
        // ink overflows — the ref paints the 130px row out of the 100px
        // synthetic root exactly like this.
        // P13 — a right run in a BOUNDED containing block claims the full
        // content width so its members (already right-aligned within the
        // plan extent, offset below) anchor at the containing block's
        // right edge (§9.5.1 rule 1 mirrored); left runs and unbounded
        // right runs keep the bare-extent report byte-identically.
        val reportedW =
            if (rightPacked && constraints.hasBoundedWidth) constraints.maxWidth
            else plan.width.roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val reportedH = plan.height.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(reportedW, reportedH) {
            // P13 — right runs shift so the plan's right edge sits on the
            // reported box's right edge (0 when reported == extent, i.e.
            // the shrink-to-fit case; negative when overconstrained —
            // right floats then overflow LEFT, matching the browser).
            val xOffset = if (rightPacked) reportedW - plan.width.roundToInt() else 0
            // P9 — whole-px placement (roundToInt), ties half-up, the
            // same rounding the abspos measure wrappers pin.
            placeables.forEachIndexed { i, p ->
                // place() beyond the reported box is legal and draws
                // unclipped — the overflow half of the P7 contract.
                p.place(xOffset + plan.x[i].roundToInt(), plan.y[i].roundToInt())
            }
        }
    }
}
