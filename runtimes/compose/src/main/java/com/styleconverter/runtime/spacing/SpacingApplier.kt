package com.styleconverter.runtime.spacing

// Back-compat facade over the per-family Appliers. LayoutFacade calls
// applyPadding / applyMargin from here; the real work is done by
// PaddingApplier.apply / MarginApplier.apply using the Phase 1 LengthValue
// primitives. Keeps the call sites stable during Phase 2 migration.

import androidx.compose.ui.Modifier

object SpacingApplier {

    /** Delegate to PaddingApplier. Uses default SpacingContext (font 16/viewport 390x844). */
    fun applyPadding(modifier: Modifier, config: PaddingConfig): Modifier =
        PaddingApplier.apply(modifier, config)

    /** Delegate to MarginApplier. [collapsed] is the optional CSS2 §8.3.1
     *  margin-collapse override from the parent block container's plan
     *  (see BlockMarginCollapse) — null everywhere outside the block loop.
     *
     *  wave-45 lane X4: [ctx] is the element's spacing-resolution context
     *  (StyleApplier.buildSpacingContext — carries the quirk-corrected own
     *  font-size, css-values-4 §5.1.1 / CSS Fonts 4 §3.5). Margins were the
     *  ONE spacing family still resolving against the 16px-default context,
     *  which made an em margin preserved by MarginExtractor (the prebaked-em
     *  shape) collapse back to the pre-pass value; threading the real ctx is
     *  what lets `margin: 1em` on a monospace no-font-size element render
     *  13px like the browser ref. The default keeps every legacy call site
     *  byte-identical (v × 16 equals the prebaked fallback by construction). */
    fun applyMargin(
        modifier: Modifier,
        config: MarginConfig,
        collapsed: CollapsedMargin? = null,
        ctx: SpacingContext = SpacingContext(),
    ): Modifier =
        MarginApplier.apply(modifier, config, ctx = ctx, collapsed = collapsed)
}
