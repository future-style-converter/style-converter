package com.styleconverter.runtime.effects.filter

// Pins the `filter: blur()` sigma conversion. Context: Modifier.blur was
// handed the CSS length UNCONVERTED, but filter-effects-1 §6.1 says that
// length IS the Gaussian standard deviation while Skia's radius maps to
// σ ≈ radius·0.57735 + 0.5. Android therefore under-blurred by a factor
// that DRIFTED with radius (the relation is affine, not a scale).
//
// Measured with the step-edge estimator — the same technique the iOS
// BackdropBlur lane pins ciRadiusPerSigma with. σ ratio Android/web,
// before the fix: 0.93, 0.75, 0.68, 0.68 at R = 1, 2, 4, 8.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterBlurSigmaTest {

    /** Skia's documented radius→σ map, used here as the ORACLE. */
    private fun skiaSigma(radius: Float): Float = radius * 0.57735f + 0.5f

    @Test
    fun `round trips through skia's own relation`() {
        // The property that matters: whatever radius we hand Skia must come
        // back out as the σ the spec asked for. Testing the round trip
        // rather than the formula means a sign slip or a swapped constant
        // cannot pass by restating the implementation.
        for (sigma in listOf(1f, 2f, 3f, 4f, 8f, 16f)) {
            val radius = FilterApplier.blurSigmaToSkiaRadius(sigma)
            assertEquals("σ=$sigma must round-trip", sigma, skiaSigma(radius), 0.001f)
        }
    }

    @Test
    fun `blur is NOT the drop-shadow convention`() {
        // The bug in one assertion. drop-shadow's <blur-radius> r means
        // σ = r/2 (§10.1); blur()'s parameter IS σ (§8.2). If someone
        // "unifies" the two helpers, this fails.
        assertTrue(
            "blur(4) must produce a LARGER radius than drop-shadow blur 4",
            FilterApplier.blurSigmaToSkiaRadius(4f) > FilterApplier.dropShadowMaskRadius(4f)
        )
        // Concretely: blur(4) wants σ=4 → radius 6.062; drop-shadow 4 wants
        // σ=2 → radius 2.598.
        assertEquals(6.062f, FilterApplier.blurSigmaToSkiaRadius(4f), 0.01f)
        assertEquals(2.598f, FilterApplier.dropShadowMaskRadius(4f), 0.01f)
    }

    @Test
    fun `passing the css length through unconverted would under-blur`() {
        // Pins the DEFECT, so a revert is loud. Handing Skia the raw CSS
        // length gives σ = 0.577R + 0.5, which is short of R for every
        // R > ~1.18 and diverges as R grows.
        for (r in listOf(2f, 4f, 8f)) {
            assertTrue("raw R=$r would under-blur", skiaSigma(r) < r)
        }
        // …and the shortfall grows, which is why the measured ratio drifted
        // rather than sitting at a constant.
        assertTrue(skiaSigma(8f) / 8f < skiaSigma(2f) / 2f)
    }

    @Test
    fun `sub-half-pixel sigma floors at zero rather than going negative`() {
        // Skia's +0.5 offset makes σ < 0.5 unreachable. A negative radius
        // would be rejected or wrap; flooring renders no blur, which is the
        // honest degradation.
        assertEquals(0f, FilterApplier.blurSigmaToSkiaRadius(0.4f), 0.0001f)
        assertEquals(0f, FilterApplier.blurSigmaToSkiaRadius(0f), 0.0001f)
        assertTrue(FilterApplier.blurSigmaToSkiaRadius(1f) > 0f)
    }
}
