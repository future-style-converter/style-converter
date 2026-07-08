package com.styleconverter.runtime.effects.filter

// Pins the drop-shadow blur conversion used by FilterApplier's rewritten
// shadow path. Context: the old implementation drew a full-size rect at
// (0,0) with Paint.setShadowLayer — which hardware canvases only honour
// for TEXT draws, so `filter: drop-shadow(...)` rendered NO shadow at all
// on Android (filter-functions 016/017 vs web). The new path draws the
// offset silhouette blurred with a BlurMaskFilter whose radius comes from
// dropShadowMaskRadius.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterDropShadowMathTest {

    @Test
    fun `spec sigma is half the css blur radius`() {
        // drop-shadow(2px 2px 4px …): r=4 → σ=2 → BlurMaskFilter radius
        // (σ − 0.5)/0.57735 ≈ 2.598.
        assertEquals(2.598f, FilterApplier.dropShadowMaskRadius(4f), 0.01f)
    }

    @Test
    fun `tiny blur stays positive`() {
        // BlurMaskFilter(radius ≤ 0) throws IllegalArgumentException — the
        // conversion must clamp: r=1px → σ=0.5 → raw radius 0 → floor 0.1.
        assertTrue(FilterApplier.dropShadowMaskRadius(1f) > 0f)
        assertTrue(FilterApplier.dropShadowMaskRadius(0.1f) > 0f)
    }
}
