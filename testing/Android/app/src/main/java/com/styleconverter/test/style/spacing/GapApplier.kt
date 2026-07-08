package com.styleconverter.test.style.spacing

// GapApplier — unlike padding/margin, gap does not become a Modifier. It is
// consumed by the container (Row/Column/LazyGrid) at layout-construction
// time. ComponentRenderer.buildDisplayConfig already pulls Row/Column gap
// out of IR directly; this Applier exposes a simple helper used by the new
// dispatcher when the spec'd `gap` eventually migrates to this triplet.
//
// For Phase 2 we expose a single resolveToDpPair(ctx) helper; the container
// logic can continue reading ComponentRenderer's DisplayConfig unchanged.
// This keeps the refactor bounded — gap rendering was correct before and we
// don't need to touch it, we only need to register the extractor.

import androidx.compose.ui.unit.Dp

object GapApplier {

    /**
     * Resolve (rowGap, columnGap) to concrete Dp using [ctx]. Returns 0.dp
     * for any unset side, matching the CSS initial value.
     */
    fun resolve(config: GapConfig, ctx: SpacingContext = SpacingContext()): Pair<Dp, Dp> {
        val row = resolveToDp(config.rowGap, ctx)
        val col = resolveToDp(config.columnGap, ctx)
        return row to col
    }
}
