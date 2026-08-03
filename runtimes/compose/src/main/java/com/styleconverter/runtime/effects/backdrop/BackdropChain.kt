package com.styleconverter.runtime.effects.backdrop

// The chain is built from the SAME FilterFunction values the shared
// FilterExtractor produces for `filter` and `backdrop-filter` — one parse,
// two consumers, so backdrop can never drift from the element filter path.
import com.styleconverter.runtime.effects.filter.FilterFunction
import androidx.compose.ui.unit.Dp
import kotlin.math.sqrt

/**
 * One backdrop filter operation this lane can render.
 *
 * The lane is scoped to invert + blur: 9 of the 11 measured filter-effects
 * bucket-A tests use `backdrop-filter: invert(1)`, and the blur family is the
 * one that needs the edge-clamp care documented in [BackdropSampleGeometry].
 * Anything else (grayscale, brightness, drop-shadow, url(#f), …) is NOT
 * silently dropped into a partial render: [BackdropChain.of] refuses the whole
 * chain and the caller keeps the historical no-op.
 */
sealed interface BackdropOp {
    /**
     * `invert(<amount>)` — filter-effects-1 §8.6. Per-channel, in sRGB:
     * `c' = amount + c·(1 − 2·amount)` (see [BackdropChain.invertChannel]).
     */
    data class Invert(val amount: Float) : BackdropOp

    /**
     * `blur(<length>)` — filter-effects-1 §8.2. The length IS the Gaussian
     * standard deviation (unlike `drop-shadow`'s blur radius, which is 2σ —
     * that conversion lives in FilterApplier.dropShadowMaskRadius).
     */
    data class Blur(val radius: Dp) : BackdropOp
}

/**
 * An ordered, renderable backdrop filter chain.
 *
 * Order is preserved because CSS applies filter functions left to right and
 * the two differ: `invert(1) blur(4px)` blurs the inverted backdrop, while
 * `blur(4px) invert(1)` inverts the blurred one. The painter builds its
 * RenderEffect chain in this same order.
 */
data class BackdropChain(val ops: List<BackdropOp>) {

    /** True when any op is a blur — the painter needs the padded sample then. */
    val hasBlur: Boolean get() = ops.any { it is BackdropOp.Blur }

    /**
     * Effective Gaussian σ (px) of the whole chain, for sizing the sample
     * padding only. Successive Gaussians compose as σ = √(Σσᵢ²), so that is
     * what we use; with the single blur every in-scope test carries it is
     * just that blur's σ. [radiusToPx] is passed in so this stays a pure
     * function testable off-device (Dp→px needs a Density at draw time).
     */
    fun totalBlurSigmaPx(radiusToPx: (Dp) -> Float): Float {
        // Sum of squares over the blur ops; non-blur ops contribute nothing.
        var sumSq = 0f
        for (op in ops) if (op is BackdropOp.Blur) {
            val sigma = blurSigmaPx(radiusToPx(op.radius))
            sumSq += sigma * sigma
        }
        // √0 is 0 — a chain with no blur reports no spread, so the sample
        // rect collapses to the element's own border box.
        return sqrt(sumSq)
    }

    companion object {
        /**
         * Classify a `backdrop-filter` function list into a renderable chain,
         * or null when this lane cannot render it faithfully.
         *
         * Null is the honest answer, not a fallback: the caller responds by
         * keeping the pre-existing no-op (nothing is drawn, the element still
         * paints its own box), which is what shipped before this lane.
         */
        fun of(filters: List<FilterFunction>): BackdropChain? {
            // `backdrop-filter` absent / empty list → nothing to coordinate.
            if (filters.isEmpty()) return null
            val ops = ArrayList<BackdropOp>(filters.size)
            for (f in filters) {
                when (f) {
                    // In-scope: the two families this lane renders.
                    // Wave 26 contract unification — IDENTITY ops are SKIPPED
                    // (not admitted as no-op work), matching the iOS twin's
                    // BackdropPlan.plan: invert(0) and blur(<=0) are exact
                    // identities (filter-effects-1 §8.6/§8.2), so dropping
                    // them is lossless; a chain of ONLY identities then
                    // plans empty and the applier takes the identity branch
                    // on BOTH natives (no pass-A suppression — visually
                    // equal on a single shared canvas, and now byte-parallel).
                    is FilterFunction.Invert ->
                        if (f.amount > 0f) ops.add(BackdropOp.Invert(f.amount))
                    is FilterFunction.Blur ->
                        if (f.radius.value > 0f) ops.add(BackdropOp.Blur(f.radius))
                    // `backdrop-filter: none` is a valid identity — it carries
                    // no op, and a chain of nothing but `none` falls through to
                    // the empty check below (no pass-B work, no suppression).
                    is FilterFunction.None -> Unit
                    // Out of lane scope. Refusing the WHOLE chain (rather than
                    // rendering the recognised prefix) keeps the cross-platform
                    // comparison honest: a half-applied chain would look like a
                    // rendering bug instead of an unimplemented value flavour.
                    else -> return null
                }
            }
            return if (ops.isEmpty()) null else BackdropChain(ops)
        }

        /**
         * filter-effects-1 §8.6 invert, per sRGB channel, in 0..1:
         * `c' = amount·(1 − c) + (1 − amount)·c = amount + c·(1 − 2·amount)`.
         *
         * Same curve the element-filter path already ships as a ColorMatrix
         * (FilterApplier.applyInvert: scale = 1 − 2a, translate = a). Pinned
         * here as scalar math so the identity (a=0), the full inversion (a=1)
         * and the mid-point collapse (a=0.5 → 0.5 for every input) are
         * checkable without a device.
         */
        fun invertChannel(amount: Float, channel: Float): Float =
            amount + channel * (1f - 2f * amount)

        /**
         * The 4×5 row-major colour matrix for `invert(amount)` in Android's
         * 0..255 convention (the translate column is scaled by 255, the same
         * convention android.graphics.ColorMatrix and Compose's ColorMatrix
         * both use). Alpha is untouched: filter-effects-1 defines invert on
         * the colour channels only.
         */
        fun invertColorMatrix(amount: Float): FloatArray {
            // Clamp: percentages above 100% clamp to 1 per §8.6.
            val a = amount.coerceIn(0f, 1f)
            // Slope and intercept of the same line as invertChannel, with the
            // intercept expressed in 0..255.
            val scale = 1f - 2f * a
            val translate = a * 255f
            return floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        }

        /**
         * CSS `blur(<length>)` → Gaussian σ. Identity by definition — the
         * spec's parameter IS the standard deviation — but named so the claim
         * is one greppable place and cannot be confused with the shadow
         * families where the radius is 2σ.
         */
        fun blurSigmaPx(radiusPx: Float): Float = if (radiusPx > 0f) radiusPx else 0f
    }
}
