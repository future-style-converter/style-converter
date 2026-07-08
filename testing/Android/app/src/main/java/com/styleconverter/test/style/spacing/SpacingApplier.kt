package com.styleconverter.test.style.spacing

// Back-compat facade over the per-family Appliers. LayoutFacade calls
// applyPadding / applyMargin from here; the real work is done by
// PaddingApplier.apply / MarginApplier.apply using the Phase 1 LengthValue
// primitives. Keeps the call sites stable during Phase 2 migration.

import androidx.compose.ui.Modifier

object SpacingApplier {

    /** Delegate to PaddingApplier. Uses default SpacingContext (font 16/viewport 390x844). */
    fun applyPadding(modifier: Modifier, config: PaddingConfig): Modifier =
        PaddingApplier.apply(modifier, config)

    /** Delegate to MarginApplier. */
    fun applyMargin(modifier: Modifier, config: MarginConfig): Modifier =
        MarginApplier.apply(modifier, config)
}
