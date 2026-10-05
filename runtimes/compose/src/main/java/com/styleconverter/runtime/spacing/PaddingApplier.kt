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
// Wave 52 (lane L4, queue 0(b″)) — WHICH containing block. The composed
// factory materialises INSIDE this element's own CompositionLocalProvider
// (ComponentRenderer.inheritanceWrappedContent), so a bare
// `LocalContainingBlock.current` there is the block this element publishes
// for its CHILDREN — one level too deep, the same defect wave 50 (B2) fixed
// for percentage insets. On css-sizing/abspos-auto-sizing-fit-content-
// percentage-003/004 (android P 0.9984) the `.child` (Width 100 × Height
// 100, `padding-left: 50%` / `padding-right: 50%`) sits in a fit-content
// abspos `.abs` that publishes (null, null): CSS 2.1 §8.4 + css-sizing-3
// §5.2.1 give 0 px, the child-level read gave 50 % of its OWN 100 px = 50 px
// — INSIDE the fixed 100 px box (content 50, not 100), which the picture
// never showed because the box paints nothing and has no children, while
// SizingExtractor's frame-inflation lane (P13) already used 0 for the same
// box. The percent lane now reads the ELEMENT-level channel through
// ElementContainingBlock.containingBlockFor; the WPT gate and the tri-state
// are untouched, so outside WPT capture nothing changes.
//
// Call sites: LayoutFacade.applyToModifier, SpacingApplier (back-compat shim).

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.renderer.LocalWptCaptureMode
import com.styleconverter.runtime.core.variables.LocalContainingBlock
// Wave 52 (b″): the element-level containing-block channel + its breadcrumb.
import com.styleconverter.runtime.layout.position.ElementContainingBlock

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
                // the harness capture root). The ELEMENT-level block (CSS 2.1
                // §10.1 — the block this padding's box is laid out in); the
                // child-level ambient read is kept only as the breadcrumbed
                // fallback for paths the renderer's provider never wraps.
                val cb = ElementContainingBlock.containingBlockFor(
                    element = ElementContainingBlock.LocalElementContainingBlock.current,
                    ambient = LocalContainingBlock.current,
                    breadcrumb = ElementContainingBlock.SPACING_UNPUBLISHED_BREADCRUMB,
                )
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
