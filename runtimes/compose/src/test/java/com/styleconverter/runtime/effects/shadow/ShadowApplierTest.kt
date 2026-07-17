package com.styleconverter.runtime.effects.shadow

// Pins the Lane S box-shadow fixes on Android:
//  1. The elevation fast path is GONE — a single 0-offset/0-spread colored
//     shadow used to route through Modifier.shadow(elevation), Android's
//     physically-modeled z-shadow with fixed ambient/spot alphas, so
//     `box-shadow: 0 0 40px red` rendered a faint grey rim instead of the
//     wide red Gaussian halo css-backgrounds-3 §7.1 specifies. Every
//     shadow must now build the drawBehind + BlurMaskFilter chain.
//  2. The CSS→Skia blur conversion — css-backgrounds-3 §7.1 says blur
//     radius r ⇒ Gaussian σ = r/2 (Chromium's rule); Skia's BlurMaskFilter
//     maps radius → σ ≈ 0.57735·radius + 0.5, so the applier must invert:
//     radiusForSkia = max(0, (r/2 − 0.5)/0.57735). Passing the raw CSS
//     radius (old behavior) over-blurred by ~2.3×.

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowApplierTest {

    // Fold the modifier chain into the concrete Modifier.Element class
    // names — lets us assert WHICH draw strategy was chosen without a
    // renderer: drawBehind produces androidx…draw.DrawBehindElement,
    // while the removed elevation path produced a graphics-layer shadow
    // element (ShadowGraphicsLayerElement in Compose 1.7).
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    @Test
    fun `zero-offset colored shadow does not route to elevation`() {
        // Exactly the shape the old fast path matched: single layer, no
        // offset, no spread, not inset — `box-shadow: 0 0 40px red`.
        val config = ShadowConfig(
            shadows = listOf(ShadowData(blurRadius = 40.dp, color = Color.Red))
        )
        val names = elementNames(ShadowApplier.applyFullShadow(Modifier, config))
        // One outset bucket → exactly one draw element, and it must be the
        // custom Gaussian path, not any elevation/graphics-layer shadow.
        assertEquals(names.joinToString(), 1, names.size)
        assertTrue(names[0], names[0].contains("DrawBehind"))
        assertTrue(
            "elevation fast path resurfaced: $names",
            names.none { it.contains("GraphicsLayer") || it.contains("ShadowElement") }
        )
    }

    @Test
    fun `public applyShadow entry also avoids elevation`() {
        // The EffectsFacade entry point (applyShadow → applyFullShadow with
        // the default no-radius config) hit the same fast-path condition
        // (!radiusConfig.hasRadius) — pin it at the public surface too.
        val config = ShadowConfig(
            shadows = listOf(ShadowData(blurRadius = 6.dp, color = Color.Black))
        )
        val names = elementNames(ShadowApplier.applyShadow(Modifier, config))
        assertTrue(names.toString(), names.any { it.contains("DrawBehind") })
        assertTrue(names.toString(), names.none { it.contains("GraphicsLayer") })
    }

    @Test
    fun `skia mask radius inverts the sigma mapping`() {
        // r=40 → σ=20 → radius (20 − 0.5)/0.57735 ≈ 33.775.
        assertEquals(33.775f, ShadowApplier.blurMaskRadius(40f), 0.01f)
        // r=4 → σ=2 → radius ≈ 2.598 (same constant FilterApplier pins for
        // drop-shadow — box-shadow and drop-shadow share the spec rule).
        assertEquals(2.598f, ShadowApplier.blurMaskRadius(4f), 0.01f)
    }

    @Test
    fun `sub-pixel blur clamps to zero instead of going negative`() {
        // r=1 → σ=0.5 → raw radius exactly 0 — the call sites must then
        // SKIP BlurMaskFilter (it throws on radius ≤ 0), rendering crisp.
        assertEquals(0f, ShadowApplier.blurMaskRadius(1f), 0f)
        // r=0.5 → raw radius −0.433 → clamped to 0, never negative.
        assertEquals(0f, ShadowApplier.blurMaskRadius(0.5f), 0f)
        // r=0 (no blur requested) → 0.
        assertEquals(0f, ShadowApplier.blurMaskRadius(0f), 0f)
    }
}
