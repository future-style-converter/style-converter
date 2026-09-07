package com.styleconverter.runtime.sizing

// CalcSizeLayout — the Modifier halves of css-values-5 §11 calc-size().
//
// Wave 42 (lane W3). Two lanes, both driven by CalcSizeMath's JVM-pinned
// arithmetic (CalcSizeValue.kt) and both following the house overflow
// pattern (ExactWidthOverflow): measure the CONTENT at the resolved target,
// report a size the incoming envelope allows, anchor the placeable at the
// START edge so the surplus ink spills toward inline-END exactly like the
// browser's overflow:visible — never requiredWidth's centered spill.
//
//  * PREFERRED lane (width/height slots): the box's used size IS
//    `factor * basis + offset`; the basis is the content's intrinsic size
//    (CalcSizeMath.preferredBasis). Carries EXPLICIT intrinsic overrides —
//    css-values-5 says a calc-size() box's min-/max-content CONTRIBUTIONS
//    substitute the corresponding intrinsic for `size`, and that is what a
//    `width: fit-content` PARENT reads (calc-size-min-max-sizes-001: parent
//    squeezed to 0 must see child min-contribution 20 + 80 = 100 to paint
//    the ref's 100×100 square).
//  * MIN (floor) lane (min-width/min-height slots): the used main size
//    never falls below the resolved floor; a too-small container is
//    overflowed, not honoured (css-flexbox-1 §4.5 — the calc-size-flex
//    family's automatic minimum, where Android painted NO green because
//    the un-floored item collapsed to the container's zero main size).
//
// Intrinsic reads on the MEASURE pass go through IntrinsicChannel.probe
// (SubcomposeLayout subtrees THROW on intrinsics; an unguarded probe kills
// the whole capture — the wave-39 lesson banked in IntrinsicChannel). The
// intrinsic OVERRIDE methods forward raw, mirroring
// BreakWordMinIntrinsicModifier's audit note: an override only runs inside
// somebody's intrinsic query, which this runtime always issues through a
// guarded caller, so the refusal surfaces at that caller — never here.

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
import com.styleconverter.runtime.layout.IntrinsicChannel

/** Log tag — refusals must point at the calc-size lane, not the guard. */
private const val TAG = "CalcSizeLayout"

/** One refusal sentence per lane, per the no-silent-fallthrough rule. */
private fun refusal(lane: String) =
    "css-values-5 calc-size() $lane skipped — this box's subtree has no " +
        "intrinsic channel to resolve the `size` basis; the box keeps the " +
        "measure its incoming constraints give it (the pre-typed-wire " +
        "dropped-declaration geometry, mis-sized at worst, alive)."

/**
 * Probe BOTH main-axis intrinsics of [measurable] through the guarded
 * channel. Either read refusing yields null (one logged refusal — the
 * guard's set-keyed log-once collapses the pair) so callers fall back
 * atomically instead of acting on half a basis. `internal` because the
 * MIN lane (CalcSizeMinLayout.kt, the ~300-line split twin of this file)
 * shares it — one guard, one wording, no drift.
 */
internal fun probeIntrinsics(
    measurable: IntrinsicMeasurable,
    rowAxis: Boolean,
    crossHint: Int,
    lane: String,
): Pair<Int, Int>? {
    // min first, max second; each independently guarded.
    val mn = IntrinsicChannel.probe(TAG, refusal(lane)) {
        if (rowAxis) measurable.minIntrinsicWidth(crossHint)
        else measurable.minIntrinsicHeight(crossHint)
    } ?: return null
    val mx = IntrinsicChannel.probe(TAG, refusal(lane)) {
        if (rowAxis) measurable.maxIntrinsicWidth(crossHint)
        else measurable.maxIntrinsicHeight(crossHint)
    } ?: return null
    return mn to mx
}

/**
 * PREFERRED lane. [rowAxis] true = width slot, false = height slot.
 * Attached by SizingApplier where Modifier.width/height would have gone,
 * so background (StyleApplier step 6, chained INSIDE) paints at the
 * resolved target — the ink the SSIM comparison sees.
 */
internal class CalcSizePreferredModifier(
    private val rowAxis: Boolean,
    private val spec: CalcSizeValue,
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Cross-axis hint for the intrinsic reads: the bounded incoming
        // cross constraint, else Infinity — the same hint shape
        // FlexAutoMinSize passes (a wrapping child answers for the width
        // it will actually lay out at).
        val crossHint = if (rowAxis) {
            if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        } else {
            if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        }
        // Guarded intrinsic probes — both reads or an atomic fallback.
        // REFUSED ⇒ no basis is knowable: measure with the constraints the
        // parent handed down — byte-for-byte the dropped-declaration
        // geometry this lane replaces (logged once by the probe).
        val intrinsics = probeIntrinsics(measurable, rowAxis, crossHint, "preferred size")
        if (intrinsics == null) {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        val (minI, maxI) = intrinsics
        val incomingMax = if (rowAxis) constraints.maxWidth else constraints.maxHeight
        // Pure decision: basis px for this measure pass.
        val basisPx = CalcSizeMath.preferredBasis(spec.basis, minI, maxI, incomingMax)
            ?: run {
                // fit-content basis with a half-refused channel — same
                // fallback contract as the full refusal above.
                val placeable = measurable.measure(constraints)
                return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
        val target = spec.targetPx(basisPx)
        // House overflow split (ExactWidthOverflow.measureSpec): measure the
        // content at the DECLARED size; report the envelope-clamped size so
        // the parent's flow geometry cannot grow; ink spills start-anchored.
        val (childPx, reportedPx) = ExactWidthOverflow.measureSpec(
            target,
            if (rowAxis) constraints.minWidth else constraints.minHeight,
            incomingMax,
        )
        // Tight constraint on OUR axis; the cross axis passes through — the
        // exact transformation Modifier.width/height performs.
        val childConstraints = if (rowAxis)
            constraints.copy(minWidth = childPx, maxWidth = childPx)
        else
            constraints.copy(minHeight = childPx, maxHeight = childPx)
        val placeable = measurable.measure(childConstraints)
        // Report the clamped size on our axis, the child's own on the other.
        val w = if (rowAxis) reportedPx else placeable.width
        val h = if (rowAxis) placeable.height else reportedPx
        return layout(w, h) {
            // Start-anchored: surplus spills toward inline-END (LTR right),
            // matching the browser's overflow side — see ExactWidthOverflow.
            placeable.placeRelative(0, 0)
        }
    }

    // ── Intrinsic contributions (css-values-5 substitution rule) ─────────
    // A min-content query substitutes the child's min intrinsic for `size`
    // on contribution-kind bases (auto/fit-content/stretch/content); a
    // max-content query substitutes the max. Fixed-kind bases (min-content
    // / max-content keywords) answer the SAME substituted value to both.

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable, height: Int,
    ): Int = if (!rowAxis) measurable.minIntrinsicWidth(height) // height lane: width passes through
    else spec.targetPx(CalcSizeMath.intrinsicBasis(
        spec.basis, wantMin = true,
        measurable.minIntrinsicWidth(height), measurable.maxIntrinsicWidth(height)))

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable, height: Int,
    ): Int = if (!rowAxis) measurable.maxIntrinsicWidth(height)
    else spec.targetPx(CalcSizeMath.intrinsicBasis(
        spec.basis, wantMin = false,
        measurable.minIntrinsicWidth(height), measurable.maxIntrinsicWidth(height)))

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable, width: Int,
    ): Int = if (rowAxis) measurable.minIntrinsicHeight(width) // width lane: height passes through
    else spec.targetPx(CalcSizeMath.intrinsicBasis(
        spec.basis, wantMin = true,
        measurable.minIntrinsicHeight(width), measurable.maxIntrinsicHeight(width)))

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable, width: Int,
    ): Int = if (rowAxis) measurable.maxIntrinsicHeight(width)
    else spec.targetPx(CalcSizeMath.intrinsicBasis(
        spec.basis, wantMin = false,
        measurable.minIntrinsicHeight(width), measurable.maxIntrinsicHeight(width)))
}

/** Factory helper so SizingApplier reads declaratively. */
internal fun Modifier.calcSizePreferred(rowAxis: Boolean, spec: CalcSizeValue): Modifier =
    this.then(CalcSizePreferredModifier(rowAxis, spec))
