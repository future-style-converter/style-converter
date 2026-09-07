package com.styleconverter.runtime.sizing

// PercentSizeClamp — retro R2 (A11#10): a PERCENT width/height that also
// carries a min/max on the same axis (split out of SizingApplier, which sits
// at the house ceiling).
//
// ## The measured defect (A11 pairwise net, fixtures/fidelity/pairwise)
// pairs-01 031 PW_Background_Sizing_04: `inline-size: 50%; min-inline-size:
// 300px` renders 179 wide on Android (358 × 0.5) against 300 on iOS and web.
// CSS 2.1 §10.4: the used width is the percentage-resolved value clamped by
// min-width/max-width — max(300, 179) = 300. Compose cannot express that with
// the two stock modifiers SizingApplier chained: `fillMaxWidth(0.5)` is OUTER
// and hands the child a TIGHT 179..179 band, so the INNER `widthIn(min =
// 300)` (SizeNode, enforceIncoming) is coerced back into 179. Reordering the
// two is not a fix either — `widthIn(max = 100)` outside `fillMaxWidth(0.5)`
// makes the fraction resolve against the CLAMPED 100 (→ 50) instead of the
// containing block (→ 179, then min(179, 100) = 100 per §10.4).
//
// So the axis needs ONE layout-time step that (1) resolves the percentage
// against the incoming max — exactly what FillNode does — and (2) clamps the
// result by min/max with min winning (§10.4: "if the computed value of
// min-width is greater than the value of max-width, max-width is set to the
// value of min-width"; §10.7 for heights). The pure half below is JVM-pinned
// (SizingMinMaxClampTest); the modifier half is a thin Modifier.layout over
// it, the same shape as ExactWidthOverflow.
//
// ## Blast radius (executed scans, wave49-final per-test IR + fixtures/**)
// SizingApplier routes here ONLY when the axis is a PERCENT Relative AND at
// least one of min/max on that axis resolves — every other config keeps its
// byte-identical fillMaxWidth/Height + widthIn/heightIn chain. Carriers:
// dark-stage pairs-01 PW_Background_Sizing_04 (the 179 → 300 mover, toward
// iOS/web); WPT wave49-final has exactly FOUR carriers (scan of all 1435
// per-test IR docs, percent shape `{"type":"percentage","value":N}`):
// css-tables height-distribution percentage-sizing-of-table-cell-007
// (Android FAIL 0.9412) and -children-003/004/006 (Android PASS 0.9954),
// all `height: 100%; min-height: 100px`. At fraction 1.0 this lane is
// ARITHMETICALLY IDENTICAL to the fill + heightIn chain it replaces in
// every constraint case — bounded: round(max × 1) = max ≥ min ? max :
// (min coerced back into (…, max) = max), exactly FillNode's tight result;
// unbounded: the (min, ∞) band SizeNode produced — so none of the four can
// move (pinned: `bounded - the incoming band still coerces the result`,
// `bounded - no bounds is FillNode parity`, `unbounded - …`). The lane only
// diverges when min > round(max × fraction) with fraction < 1 (the 179 →
// 300 case) or when a max caps the percent-resolved size.

import androidx.compose.ui.Modifier
// The custom measure hook (Modifier.layout) — same mechanism as
// ExactWidthOverflow.exactWidth and the renderer's absposOverflowMeasure.
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

internal object PercentSizeClamp {

    /**
     * Pure: the (min, max) constraint band to measure the child with on one
     * axis. JUnit-pinned on both sides of the bounded/unbounded split.
     *
     *  - BOUNDED incoming max: FillNode's own arithmetic — `round(max ×
     *    fraction)` — with the §10.4/§10.7 clamp inserted BEFORE FillNode's
     *    coercion into the incoming band (kept, so a clamp can never violate
     *    the parent's constraints any more than fillMax* could). Tight band.
     *  - UNBOUNDED incoming max: the percentage has no containing-block
     *    size to resolve against (FillNode is an identity here), so only the
     *    min/max band applies — SizeNode(enforceIncoming) semantics via
     *    Constraints.constrain, EXCEPT that a min above the max raises the
     *    max instead of being coerced down to it (min wins, §10.4).
     *
     * @param incomingMin / incomingMax the incoming constraints on this axis
     *   (max may be [Constraints.Infinity]).
     * @param fraction the percentage as 0..1 (SizingApplier's coerced value).
     * @param minPx / maxPx the resolved min/max in px, null when absent.
     */
    internal fun band(
        incomingMin: Int,
        incomingMax: Int,
        fraction: Float,
        minPx: Int?,
        maxPx: Int?,
    ): Pair<Int, Int> {
        if (incomingMax != Constraints.Infinity) {
            // FillNode: width = round(maxWidth × fraction) …
            var v = (incomingMax * fraction).roundToInt()
            // … then the CSS clamp, max first so a larger min wins (§10.4).
            if (maxPx != null) v = minOf(v, maxPx)
            if (minPx != null) v = maxOf(v, minPx)
            // … then FillNode's coercion into the incoming band, unchanged.
            v = v.coerceIn(incomingMin, incomingMax)
            return v to v
        }
        // Unbounded: min-wins target band, then Constraints.constrain against
        // (incomingMin, ∞) — i.e. both ends floored at incomingMin.
        val targetMax = if (maxPx != null) maxOf(maxPx, minPx ?: 0) else Constraints.Infinity
        val lo = maxOf(minPx ?: 0, incomingMin)
        val hi = if (targetMax == Constraints.Infinity) Constraints.Infinity else maxOf(targetMax, incomingMin)
        return lo to hi
    }
}

/**
 * Width axis: measure at the percent-resolved, min/max-clamped width per
 * [PercentSizeClamp.band]; report the child's size and place at the origin —
 * FillNode's layout() and placeRelative(IntOffset.Zero) exactly.
 */
internal fun Modifier.percentWidthClamped(fraction: Float, min: Dp?, max: Dp?): Modifier =
    this.layout { measurable, constraints ->
        // Dp→px like SizeNode: roundToPx, floored at 0.
        val (lo, hi) = PercentSizeClamp.band(
            constraints.minWidth, constraints.maxWidth, fraction,
            min?.roundToPx()?.coerceAtLeast(0), max?.roundToPx()?.coerceAtLeast(0),
        )
        // Only the width band changes; the height axis passes through.
        val placeable = measurable.measure(constraints.copy(minWidth = lo, maxWidth = hi))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

/** Height axis twin of [percentWidthClamped] (CSS 2.1 §10.7). */
internal fun Modifier.percentHeightClamped(fraction: Float, min: Dp?, max: Dp?): Modifier =
    this.layout { measurable, constraints ->
        val (lo, hi) = PercentSizeClamp.band(
            constraints.minHeight, constraints.maxHeight, fraction,
            min?.roundToPx()?.coerceAtLeast(0), max?.roundToPx()?.coerceAtLeast(0),
        )
        val placeable = measurable.measure(constraints.copy(minHeight = lo, maxHeight = hi))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
