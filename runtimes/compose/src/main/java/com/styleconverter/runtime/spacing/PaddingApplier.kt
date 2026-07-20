package com.styleconverter.runtime.spacing

// PaddingApplier — folds a PaddingConfig into a Modifier chain. For px-only
// inputs we stay byte-identical to the old SpacingApplier.applyPadding (just
// Modifier.padding with resolved sides). For em/vw/calc we fall back to a
// simple absolute resolution using a default SpacingContext.
//
// Wave-18 lane 2 — containing-block PERCENT sides (pins P10/P11/P12): a
// percent side routes through Modifier.composed so the applier can read the
// renderer's LocalContainingBlock channel (the parent's content-box width)
// and LocalWptCaptureMode at composition time — this static chain is invoked
// from non-composable facades and cannot read CompositionLocals directly.
//   * WPT capture + definite channel width → percent × that width (P10,
//     CSS 2.1 §8.4: padding-% resolves against the containing block's
//     inline size on all four sides).
//   * WPT capture + indefinite (channel null — abspos auto/fit-content
//     ancestor) → 0 (P11, css-position-3 §5.1 / css-sizing-3 §5.2.1).
//   * Non-WPT → the legacy viewport-width fallback, byte-identical to the
//     frozen dark-stage corpus (P12).
// Px-only configs never enter the composed path — identity is gated on
// usesContainingBlockPercent so committed baselines keep the exact modifier
// values they were captured with.
//
// Call sites: LayoutFacade.applyToModifier, SpacingApplier (back-compat shim).

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.renderer.LocalWptCaptureMode
import com.styleconverter.runtime.core.variables.LocalContainingBlock

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
        // Percent sides need the containing-block channel, which only the
        // composition can provide — branch to the composed path. Everything
        // else stays on the historical static path (baseline byte-stability).
        if (usesContainingBlockPercent(r.top, r.right, r.bottom, r.left)) {
            return modifier.composed {
                // Read the renderer-provided channels (additive reads only —
                // the channels are owned/provided by ComponentRenderer and
                // the harness capture root).
                val cb = LocalContainingBlock.current
                val wpt = LocalWptCaptureMode.current
                // WPT capture only: definite channel width as the percent
                // base (P10), indefinite base → 0 (P11). Outside WPT the
                // UNMODIFIED ctx keeps the legacy viewport fallback (P12) —
                // this composed wrapper then computes pixel-identical
                // values to the old static path, so the frozen dark-stage
                // corpus is untouched.
                val pctCtx = if (wpt) {
                    ctx.copy(parentWidthPx = cb.widthPx, percentIndefiniteAsZero = true)
                } else ctx
                paddingModifier(r, pctCtx)
            }
        }
        // All-static path — byte-identical to the pre-wave-18 applier.
        return modifier.then(paddingModifier(r, ctx))
    }

    /**
     * The shared side-resolution + Modifier.padding step, used by both the
     * static and the composed lanes so their arithmetic can never diverge.
     * CSS padding never goes negative (the spec clamps at 0) so we guard
     * with coerceAtLeast.
     */
    private fun paddingModifier(r: PaddingConfig.Resolved, ctx: SpacingContext): Modifier {
        val top = resolveToDp(r.top, ctx).coerceAtLeast0()
        val right = resolveToDp(r.right, ctx).coerceAtLeast0()
        val bottom = resolveToDp(r.bottom, ctx).coerceAtLeast0()
        val left = resolveToDp(r.left, ctx).coerceAtLeast0()
        return Modifier.padding(start = left, top = top, end = right, bottom = bottom)
    }

    /**
     * Small helper keeping the "no negative padding" invariant local; Dp has
     * no built-in coerceAtLeast so we inline it.
     */
    private fun Dp.coerceAtLeast0(): Dp = if (value < 0f) 0.dp else this
}
