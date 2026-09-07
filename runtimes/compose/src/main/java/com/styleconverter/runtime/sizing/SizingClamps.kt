package com.styleconverter.runtime.sizing

// SizingClamps — retro R2 (A11#10): the two pure min/max-precedence decisions
// SizingApplier consumes, split out because SizingApplier already sits far
// past the house ceiling (533 lines before this lane). Both are JVM-pinned
// in SizingMinMaxClampTest on the verbatim converter IR of the two fixtures
// the audit measured (pairs-01 PW_Background_Sizing_04, pairs-06
// PW_Sizing_Transforms_04).
//
// CSS 2.1 §10.4 (widths) / §10.7 (heights): the used size is the tentative
// (percentage-resolved or auto) size clamped by min/max, and "if the
// computed value of min-width is greater than the value of max-width,
// max-width is set to the value of min-width" — min wins. Compose's SizeNode
// (widthIn/heightIn) coerces min DOWN to max instead, and FillNode
// (fillMaxWidth/Height) hands the child a tight band no inner min can grow.

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.spacing.SpacingContext

internal object SizingClamps {

    /**
     * The inputs of one percent-clamp axis: the fraction exactly as the
     * fillMax* branch computes it plus the resolved min/max (null = absent).
     */
    internal data class PercentClampSpec(val fraction: Float, val minDp: Dp?, val maxDp: Dp?)

    /**
     * Does this axis take the percent-clamp lane (PercentSizeClamp)?
     * Non-null ONLY for a PERCENT [LengthValue.Relative] whose axis also
     * carries a resolvable min or max. Every other shape — px, auto,
     * intrinsic, calc, a percent with neither bound — returns null and keeps
     * its historical fillMaxWidth/Height + widthIn/heightIn chain.
     * The fraction is `(value / 100).coerceIn(0, 1)`, byte-identical to the
     * fill branch it replaces, so only the clamp is new.
     */
    internal fun percentClampSpec(
        v: LengthValue?,
        min: LengthValue?,
        max: LengthValue?,
        ctx: SpacingContext,
    ): PercentClampSpec? {
        if (v !is LengthValue.Relative || v.unit != LengthUnit.PERCENT) return null
        // Same reduction the widthIn/heightIn lane uses, so the two lanes
        // can never disagree about what a min/max resolves to.
        val mn = SizingApplier.toDpOrNull(min, ctx)
        val mx = SizingApplier.toDpOrNull(max, ctx)
        if (mn == null && mx == null) return null
        return PercentClampSpec((v.value.toFloat() / 100f).coerceIn(0f, 1f), mn, mx)
    }

    /**
     * The (min, max) band for widthIn/heightIn with CSS precedence.
     * Measured defect: pairs-06 PW_Sizing_Transforms_04 `min-block-size:
     * 80px; max-block-size: 50px; block-size: auto` — Android canvas 82
     * (SizeNode let max win) vs iOS/web 112 (min won). Null when neither
     * bound is present (no modifier at all, as before); otherwise max is
     * raised to min, and an absent max stays unbounded.
     */
    internal fun minMaxBand(min: Dp?, max: Dp?): Pair<Dp, Dp>? {
        if (min == null && max == null) return null
        val lo = min ?: 0.dp
        // Dp is Comparable — Dp.Infinity compares above every finite value.
        val hi = maxOf(max ?: Dp.Infinity, lo)
        return lo to hi
    }
}
