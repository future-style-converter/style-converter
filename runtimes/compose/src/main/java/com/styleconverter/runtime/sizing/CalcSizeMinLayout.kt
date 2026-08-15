package com.styleconverter.runtime.sizing

// CalcSizeMinLayout — the MIN (floor) lane of css-values-5 calc-size(),
// split from CalcSizeLayout.kt at the ~300-line house ceiling (the same
// split ExactWidthOverflow took out of SizingApplier). The shared guarded
// probe (probeIntrinsics), the refusal wording and the overflow-report
// philosophy all live in CalcSizeLayout.kt — this file is only the floor
// lane's measure + contribution logic.
//
// Wave 42 (lane W3). css-flexbox-1 §4.5 / css-sizing-3 §5.2: the used main
// size never falls below the resolved floor; a too-small container is
// OVERFLOWED, not honoured. This is the lane that repaints the
// calc-size-flex family on Android — the un-floored flex item collapsed to
// its zero-width container and painted NO GREEN (wave41-final, ssim 0.9542
// presence-veto on 001..006; colour-veto at 0.9966 on 009).

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/**
 * MIN (floor) lane. [specifiedPx] is the box's own definite width/height in
 * px when one was declared (the §4.5 "specified size suggestion" AND the
 * clamp partner: used = max(specified, floor)); null when the axis is auto.
 * When [specifiedPx] is non-null SizingApplier SKIPS the normal exact-size
 * modifier for the axis — this node owns the whole clamp so the reported
 * box and the painted ink cannot disagree.
 */
internal class CalcSizeMinModifier(
    private val rowAxis: Boolean,
    private val spec: CalcSizeValue,
    private val specifiedPx: Int?,
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Cross-axis hint, same shape as the preferred lane.
        val crossHint = if (rowAxis) {
            if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        } else {
            if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        }
        // Both intrinsics through the guarded channel (see the preferred lane).
        val intrinsics = probeIntrinsics(measurable, rowAxis, crossHint, "minimum floor")
        if (intrinsics == null) {
            // Refused ⇒ no floor is knowable; keep the incoming measure —
            // and when we own a specified size (the applier skipped the
            // exact modifier for us), still honour THAT as a plain tight
            // size so the declaration is not lost with the floor.
            val fallback = if (specifiedPx != null) {
                val (childPx, reportedPx) = ExactWidthOverflow.measureSpec(
                    specifiedPx,
                    if (rowAxis) constraints.minWidth else constraints.minHeight,
                    if (rowAxis) constraints.maxWidth else constraints.maxHeight)
                val p = measurable.measure(
                    if (rowAxis) constraints.copy(minWidth = childPx, maxWidth = childPx)
                    else constraints.copy(minHeight = childPx, maxHeight = childPx))
                Triple(p, if (rowAxis) reportedPx else p.width, if (rowAxis) p.height else reportedPx)
            } else {
                val p = measurable.measure(constraints)
                Triple(p, p.width, p.height)
            }
            return layout(fallback.second, fallback.third) { fallback.first.placeRelative(0, 0) }
        }
        val (contentMin, contentMax) = intrinsics
        val incomingMax = if (rowAxis) constraints.maxWidth else constraints.maxHeight
        // Floor basis per keyword. AUTO = the §4.5 automatic minimum
        // (min(specified, content-min) — CalcSizeMath.autoMinBasis, the
        // corpus-pinned arithmetic); the intrinsic keywords read their own
        // intrinsic; STRETCH/CONTENT approximate as the content minimum
        // (no Compose analogue — documented, not silent: the refusal-free
        // approximation only ever RAISES a collapsing box toward the ref).
        val basisPx = when (spec.basis) {
            CalcSizeBasis.AUTO -> CalcSizeMath.autoMinBasis(specifiedPx, contentMin)
            CalcSizeBasis.MIN_CONTENT -> contentMin
            CalcSizeBasis.MAX_CONTENT -> contentMax
            CalcSizeBasis.FIT_CONTENT ->
                CalcSizeMath.preferredBasis(spec.basis, contentMin, contentMax, incomingMax)
                    ?: contentMin
            CalcSizeBasis.STRETCH, CalcSizeBasis.CONTENT -> contentMin
        }
        val floor = spec.targetPx(basisPx)
        return if (specifiedPx != null) {
            // Definite preferred size: CSS clamp — used = max(preferred,
            // floor) (css-sizing-3 §5.2: min beats width). One tight
            // measure at the used size, house overflow split for the report.
            val used = maxOf(specifiedPx, floor)
            val (childPx, reportedPx) = ExactWidthOverflow.measureSpec(
                used,
                if (rowAxis) constraints.minWidth else constraints.minHeight,
                incomingMax)
            val placeable = measurable.measure(
                if (rowAxis) constraints.copy(minWidth = childPx, maxWidth = childPx)
                else constraints.copy(minHeight = childPx, maxHeight = childPx))
            layout(
                if (rowAxis) reportedPx else placeable.width,
                if (rowAxis) placeable.height else reportedPx,
            ) { placeable.placeRelative(0, 0) }
        } else {
            // Auto preferred size: raise the band floor (§4.5 — the item
            // overflows a too-small container instead of collapsing) and
            // let the content pick its size inside it.
            val (mn, mx) = CalcSizeMath.floorBand(
                if (rowAxis) constraints.minWidth else constraints.minHeight,
                incomingMax, floor)
            val placeable = measurable.measure(
                if (rowAxis) constraints.copy(minWidth = mn, maxWidth = mx)
                else constraints.copy(minHeight = mn, maxHeight = mx))
            // Report clamped into the INCOMING envelope (ExactWidthOverflow
            // philosophy: the parent's flow geometry must not grow); the
            // oversized placeable's start edge pins to the box's start.
            val w = if (rowAxis) constraints.constrainWidth(placeable.width) else placeable.width
            val h = if (rowAxis) placeable.height else constraints.constrainHeight(placeable.height)
            layout(w, h) { placeable.placeRelative(0, 0) }
        }
    }

    // ── Intrinsic contributions — min-* FLOORS the contribution ──────────
    // css-sizing-3 §5.2.1: a definite minimum floors the box's intrinsic
    // contributions. The floor's own basis substitutes per the calc-size
    // contribution rule (same as the preferred lane).

    /** Shared floor arithmetic for the four overrides below. */
    private fun contribution(wantMin: Boolean, childMin: Int, childMax: Int): Int {
        // The box's own un-floored contribution: its specified size when
        // definite, else the queried intrinsic of its content.
        val base = specifiedPx ?: (if (wantMin) childMin else childMax)
        // The floor, with `auto` substituting the §4.5 automatic minimum.
        val basisPx = when (spec.basis) {
            CalcSizeBasis.AUTO -> CalcSizeMath.autoMinBasis(specifiedPx, childMin)
            else -> CalcSizeMath.intrinsicBasis(spec.basis, wantMin, childMin, childMax)
        }
        return maxOf(base, spec.targetPx(basisPx))
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable, height: Int,
    ): Int = if (!rowAxis) measurable.minIntrinsicWidth(height)
    else contribution(true, measurable.minIntrinsicWidth(height), measurable.maxIntrinsicWidth(height))

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable, height: Int,
    ): Int = if (!rowAxis) measurable.maxIntrinsicWidth(height)
    else contribution(false, measurable.minIntrinsicWidth(height), measurable.maxIntrinsicWidth(height))

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable, width: Int,
    ): Int = if (rowAxis) measurable.minIntrinsicHeight(width)
    else contribution(true, measurable.minIntrinsicHeight(width), measurable.maxIntrinsicHeight(width))

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable, width: Int,
    ): Int = if (rowAxis) measurable.maxIntrinsicHeight(width)
    else contribution(false, measurable.minIntrinsicHeight(width), measurable.maxIntrinsicHeight(width))
}


/** Factory helper so SizingApplier reads declaratively. */
internal fun Modifier.calcSizeMin(rowAxis: Boolean, spec: CalcSizeValue, specifiedPx: Int?): Modifier =
    this.then(CalcSizeMinModifier(rowAxis, spec, specifiedPx))
