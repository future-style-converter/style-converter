package com.styleconverter.runtime.sizing

// Wave 12 — definite px widths with CSS overflow semantics (split out of
// SizingApplier, which sits at the ~300-line house ceiling).
//
// Modifier.width() COERCES the declared width into the incoming
// constraints, so the css-break .container's `inline-size: 472px` silently
// clamped to the 390px capture canvas while web rendered the full 472
// overflowing it (overflow:visible is the initial value, css-overflow-3 §3
// — the divergence was pixel-measured by the wave-12 lane). Compose's stock
// escape hatch, Modifier.requiredWidth, CENTERS the constraint-violating
// placeable inside the coerced reported box (both edges spill evenly),
// which matches no browser: the web capture overflows START-aligned. So the
// SizingApplier definite-px branch routes through the custom layout below —
// declared-width measurement, envelope-clamped report, start-anchored
// placement.

import androidx.compose.ui.Modifier
// The custom measure hook (androidx.compose.ui.layout.LayoutModifier via
// the Modifier.layout factory) — the same mechanism the renderer's
// absposOverflowMeasure wrapper uses for out-of-flow overflow.
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/** Namespace for the pure half so the JVM suite pins it without a device. */
internal object ExactWidthOverflow {

    /**
     * Pure measure guard for a definite px width: returns
     * Pair(childMeasurePx, reportedPx). JUnit-pinned
     * (SizingOverflowWidthTest) on BOTH sides of the split:
     *
     *  - FITTING (target ≤ incoming max): byte-identical arithmetic to
     *    Compose's Modifier.width — the declared width coerced into the
     *    incoming envelope, measured tight and reported as-is, so the whole
     *    non-overflowing baseline corpus keeps its frozen geometry.
     *  - OVERFLOW (target > incoming max): css-overflow-3 §3 — a definite
     *    width larger than the container still lays out at its DECLARED
     *    size and the ink overflows (overflow:visible initial). The child
     *    measures at the declared width (requiredWidth-equivalent), but the
     *    parent is told the envelope max so its own flow geometry cannot
     *    grow — matching the browser, where the 472px .container does not
     *    widen the 390px canvas, it just spills across it. Compose draws
     *    children unclipped by default, so the excess paints.
     */
    internal fun measureSpec(targetPx: Int, minWidthPx: Int, maxWidthPx: Int): Pair<Int, Int> =
        if (targetPx > maxWidthPx)
            // Overflow: measure at declared, report the clamped max.
            targetPx to maxWidthPx
        else
            // Fitting: Modifier.width()'s exact coercion, tight both ways.
            targetPx.coerceIn(minWidthPx, maxWidthPx).let { it to it }
}

/**
 * The modifier half: measure a definite-px width per
 * [ExactWidthOverflow.measureSpec] and anchor the placeable at the START
 * edge. On the fitting side the measure math AND the placeRelative(0,0)
 * placement are exactly what Compose's SizeNode (Modifier.width) performs,
 * so normal paths render unchanged; only a declared width larger than the
 * incoming max takes the overflow path.
 */
internal fun Modifier.exactWidth(width: Dp): Modifier = this.layout { measurable, constraints ->
    // Dp→px exactly like Compose's SizeNode: roundToPx, floored at 0.
    val targetPx = width.roundToPx().coerceAtLeast(0)
    // Pure guard: width to measure the child at + width to report upward.
    val (childPx, reportedPx) =
        ExactWidthOverflow.measureSpec(targetPx, constraints.minWidth, constraints.maxWidth)
    // Tight width at childPx; the height axis passes through untouched —
    // exactly Modifier.width's transformation of the constraint envelope.
    val placeable = measurable.measure(constraints.copy(minWidth = childPx, maxWidth = childPx))
    // Report reportedPx (== childPx unless overflowing) and the child's own
    // measured height, mirroring SizeNode's layout() call.
    layout(reportedPx, placeable.height) {
        // placeRelative(0, 0): start-anchored placement. Fitting case —
        // origin placement, identical to SizeNode. Overflow case — the
        // oversized placeable's START edge pins to the reported box's start
        // edge, so the excess spills toward the inline END (right in LTR,
        // matching the web capture's start-aligned overflow; mirrored under
        // RTL, which is the CSS-logical overflow side).
        placeable.placeRelative(0, 0)
    }
}
