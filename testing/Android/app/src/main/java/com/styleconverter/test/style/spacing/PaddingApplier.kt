package com.styleconverter.test.style.spacing

// PaddingApplier — folds a PaddingConfig into a Modifier chain. For px-only
// inputs we stay byte-identical to the old SpacingApplier.applyPadding (just
// Modifier.padding with resolved sides). For %/em/vw/calc we fall back to a
// simple absolute resolution using a default SpacingContext; full parent-
// width-aware % resolution is left to a future custom LayoutModifier, which
// is called out in the spec as acceptable when a cleaner approach is harder.
//
// Call sites: LayoutFacade.applyToModifier, SpacingApplier (back-compat shim).

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PaddingApplier {

    /**
     * Apply [config] to [modifier] given the resolution [ctx]. Returns the
     * input modifier unchanged if no padding is specified.
     */
    fun apply(
        modifier: Modifier,
        config: PaddingConfig,
        ctx: SpacingContext = SpacingContext(),
        isRtl: Boolean = false,
    ): Modifier {
        if (!config.hasPadding) return modifier
        // Collapse logical→physical. We don't currently receive LayoutDirection
        // from the renderer so we default to LTR; Phase 3 can plumb it.
        val r = config.resolve(isRtl = isRtl)
        // Resolve each side independently. CSS padding never goes negative
        // (the spec clamps at 0) so we guard with coerceAtLeast.
        val top = resolveToDp(r.top, ctx).coerceAtLeast0()
        val right = resolveToDp(r.right, ctx).coerceAtLeast0()
        val bottom = resolveToDp(r.bottom, ctx).coerceAtLeast0()
        val left = resolveToDp(r.left, ctx).coerceAtLeast0()
        return modifier.padding(start = left, top = top, end = right, bottom = bottom)
    }

    /**
     * Small helper keeping the "no negative padding" invariant local; Dp has
     * no built-in coerceAtLeast so we inline it.
     */
    private fun Dp.coerceAtLeast0(): Dp = if (value < 0f) 0.dp else this
}
