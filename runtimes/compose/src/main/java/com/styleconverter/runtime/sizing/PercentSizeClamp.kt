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
// all `height: 100%; min-height: 100px`. At fraction 1.0 the MEASURE pass of
// this lane is arithmetically identical to the fill + heightIn chain it
// replaced — bounded: round(max × 1) = max ≥ min ? max : (min coerced back
// into (…, max) = max), exactly FillNode's tight result; unbounded: the
// (min, ∞) band SizeNode produced (pinned: `bounded - the incoming band
// still coerces the result`, `bounded - no bounds is FillNode parity`,
// `unbounded - …`). The retro concluded from that arithmetic that "none of
// the four can move". THREE OF THEM MOVED — next section: the measure pass
// was identical, the INTRINSIC pass was not.
//
// ## The INTRINSIC pass — the wave-50 gate's three lost cells (wave 51, 0(z))
// wave50-final -children-003/-004/-006 Android went P 0.9954 → f 0.9966: the
// green `overflow-y: auto; height: 100%; min-height: 100px` cell child
// rendered 0-tall and the abspos z-index:-1 red square showed. Same per-test
// IR, same emulator, same harness app as wave 49; sibling -005 (the same
// child WITHOUT min-height but WITH a 100px content child) never moved. The
// discriminator is where the ROW gets its height. TableApplier sizes a row
// with `heightAtMinIntrinsic`, i.e. `measurable.minIntrinsicHeight(w)` over
// its cells. Compose's `Modifier.layout {}` (LayoutModifierImpl) answers an
// intrinsic query through MeasuringIntrinsics: it re-runs this measure lambda
// against a DefaultIntrinsicMeasurable whose `measure(constraints)` returns
// an EmptyPlaceable sized by the CHILD'S OWN intrinsic — the `minHeight = lo`
// the lambda passes is IGNORED there. So the floor `band()` computes never
// reaches the row: -003's child has no content, its intrinsic is 0, the row
// measures 0 tall, and the real measure pass then gets `band(0, 0, 1f, 100,
// null)` = max(0, 100) coerced into (0, 0) = 0. The pre-R2 chain had no such
// hole: `heightIn(min = 100)` is a SizeNode, whose `minIntrinsicHeight` is
// `constrainHeight(child intrinsic)` — floor 100 — and -005 survives on
// either chain because its 100px content child IS the intrinsic.
//
// The fix keeps the layout step (the §10.4/§10.7 MEASURE semantics R2
// bought) and chains the min/max SizeNode INSIDE it (`intrinsicFloor`).
// Measure-time the inner node is inert: the outer band is already tight
// (bounded) or already (min, targetMax) (unbounded), and SizeNode
// (enforceIncoming) constrains its target INTO the incoming band, so every
// number in the previous section is unchanged; intrinsic-time it restores
// the floor the old chain carried. The wave-50 bisection record
// (tools/titan/results/wave50-gate/lost-cells-bisection.json) says "lane
// disabled → still red"; it carries no evidence that its rebuilt APKs were
// ever installed and it contradicts this code reading, so wave 51 re-ran the
// A/B with the installed base.apk's sha1 verified against the build
// (BACKLOG 0(z) carries the numbers).

import androidx.compose.ui.Modifier
// The custom measure hook (Modifier.layout) — same mechanism as
// ExactWidthOverflow.exactWidth and the renderer's absposOverflowMeasure.
import androidx.compose.ui.layout.layout
// The stock SizeNodes: the ONLY modifier shape whose intrinsic answers
// constrain the child's intrinsic into the min/max target (see the banner).
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
    // INNER SizeNode — carries the min/max into the intrinsic pass (banner,
    // "The INTRINSIC pass"); inert at measure time.
    }.then(intrinsicFloor(min, max, rowAxis = true))

/** Height axis twin of [percentWidthClamped] (CSS 2.1 §10.7). */
internal fun Modifier.percentHeightClamped(fraction: Float, min: Dp?, max: Dp?): Modifier =
    this.layout { measurable, constraints ->
        val (lo, hi) = PercentSizeClamp.band(
            constraints.minHeight, constraints.maxHeight, fraction,
            min?.roundToPx()?.coerceAtLeast(0), max?.roundToPx()?.coerceAtLeast(0),
        )
        val placeable = measurable.measure(constraints.copy(minHeight = lo, maxHeight = hi))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    // Height twin of the inner SizeNode above — this is the node whose
    // absence made css-tables -children-003/-004/-006 render 0-tall.
    }.then(intrinsicFloor(min, max, rowAxis = false))

/**
 * The min/max SizeNode chained INSIDE the percent clamp so that Compose's
 * INTRINSIC pass sees the floor/ceiling (a `Modifier.layout {}` block cannot
 * carry one — banner). The band is [SizingClamps.minMaxBand], the same
 * min-wins reduction `applyWidthIn`/`applyHeightIn` use on non-percent axes,
 * so the two lanes can never disagree about what a min/max means; it is
 * non-null whenever [SizingClamps.percentClampSpec] routed here (at least
 * one bound resolved), and the `?: Modifier` is only defensive.
 *
 * @param rowAxis true → width axis (`widthIn`), false → height (`heightIn`).
 */
private fun intrinsicFloor(min: Dp?, max: Dp?, rowAxis: Boolean): Modifier {
    val (lo, hi) = SizingClamps.minMaxBand(min, max) ?: return Modifier
    return if (rowAxis) Modifier.widthIn(min = lo, max = hi) else Modifier.heightIn(min = lo, max = hi)
}
