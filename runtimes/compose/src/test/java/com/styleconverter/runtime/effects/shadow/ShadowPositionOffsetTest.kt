package com.styleconverter.runtime.effects.shadow

// Pins the positioned-element box-shadow geometry fix (the shadow half of
// the wave-27 "step 3 is chained OUTSIDE step 4" correction):
//
// StyleApplier installs the effects step (3) OUTER of the layout step (4),
// and step 4 ends with PositionApplier.applyPosition → absoluteOffset — so
// ShadowApplier's drawBehind origin is the element's UN-offset layout slot
// while the element's own background/borders paint at the offset one.
// Measured on WPT `backdrop-filter-box-shadow.html` (position:absolute;
// top:125px; left:75px; box-shadow: 20px 20px 10px #888): Chromium draws
// the grey band hugging the box inside the green container; Android drew a
// full blurred grey square up in the explanatory-text band — the
// canvas-root-hoisted un-offset origin plus the 20px shadow offset.
//
// The fix threads PositionApplier.resolvedOffset through EffectsFacade into
// ShadowApplier; the outset perimeter math lives in
// ShadowGeometry.outsetShadowRect — a pure function precisely so this JVM
// suite (no Robolectric; android.graphics is a throwing stub) can pin it.

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowPositionOffsetTest {

    // Same chain-introspection helper as ShadowApplierTest: fold the
    // modifier into its concrete element class names so we can assert the
    // draw strategy without a renderer.
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // ---------------------------------------------------------------
    // ShadowGeometry.outsetShadowRect — the value-level perimeter math
    // ---------------------------------------------------------------

    @Test
    fun `zero position offset degenerates to the pre-fix formula`() {
        // The byte-identity guarantee for every static element (the
        // overwhelming majority, and all committed baseline captures):
        // with a zero position offset each edge must equal the formula
        // the draw code used before the fix — offset ± spread around the
        // laid-out size.
        val rect = ShadowGeometry.outsetShadowRect(
            widthPx = 100f, heightPx = 50f,
            shadowOffsetXPx = 20f, shadowOffsetYPx = 10f,
            spreadPx = 5f,
        )
        assertEquals(15f, rect.left)      // 20 − 5
        assertEquals(5f, rect.top)        // 10 − 5
        assertEquals(125f, rect.right)    // 100 + 20 + 5
        assertEquals(65f, rect.bottom)    // 50 + 10 + 5
    }

    @Test
    fun `position offset translates every edge without resizing`() {
        // The WPT shape: .filter-element is 100×100 at left:75px/top:125px
        // with `box-shadow: 20px 20px 10px 0 #888888`. The perimeter must
        // be the border box slid by position + shadow offset — translation
        // only, so width/height stay 100×100.
        val rect = ShadowGeometry.outsetShadowRect(
            widthPx = 100f, heightPx = 100f,
            shadowOffsetXPx = 20f, shadowOffsetYPx = 20f,
            spreadPx = 0f,
            positionOffsetXPx = 75f, positionOffsetYPx = 125f,
        )
        assertEquals(95f, rect.left)      // 75 + 20
        assertEquals(145f, rect.top)      // 125 + 20
        assertEquals(195f, rect.right)    // 100 + 75 + 20
        assertEquals(245f, rect.bottom)   // 100 + 125 + 20
        // Translation must never change the box's size (absoluteOffset
        // moves its child without resizing it).
        assertEquals(100f, rect.right - rect.left)
        assertEquals(100f, rect.bottom - rect.top)
    }

    @Test
    fun `negative position offset pulls the shadow the other way`() {
        // A `right`/`bottom` inset resolves NEGATIVE in
        // PositionApplier.resolvedOffset (signed by design) — the shadow
        // must follow the box leftward/upward exactly as it follows it
        // rightward/downward.
        val rect = ShadowGeometry.outsetShadowRect(
            widthPx = 40f, heightPx = 40f,
            shadowOffsetXPx = 0f, shadowOffsetYPx = 0f,
            spreadPx = 2f,
            positionOffsetXPx = -30f, positionOffsetYPx = -12f,
        )
        assertEquals(-32f, rect.left)     // −30 − 2
        assertEquals(-14f, rect.top)      // −12 − 2
        assertEquals(12f, rect.right)     // 40 − 30 + 2
        assertEquals(30f, rect.bottom)    // 40 − 12 + 2
    }

    @Test
    fun `position offset and spread compose independently`() {
        // Spread inflates around the TRANSLATED box: negative spread
        // (perimeter contraction, css-backgrounds-3 §6.1) contracts the
        // already-slid rect — the two terms must never interact.
        val rect = ShadowGeometry.outsetShadowRect(
            widthPx = 60f, heightPx = 60f,
            shadowOffsetXPx = 4f, shadowOffsetYPx = 4f,
            spreadPx = -10f,
            positionOffsetXPx = 50f, positionOffsetYPx = 50f,
        )
        assertEquals(64f, rect.left)      // 50 + 4 + 10
        assertEquals(64f, rect.top)
        assertEquals(104f, rect.right)    // 60 + 50 + 4 − 10
        assertEquals(104f, rect.bottom)
    }

    // ---------------------------------------------------------------
    // Modifier chain shape — the offset must not change draw strategy
    // ---------------------------------------------------------------

    @Test
    fun `outset shadow with position offset keeps the single DrawBehind element`() {
        // The offset rides INSIDE the existing drawBehind lambda — it must
        // not add, remove, or replace chain elements (no graphicsLayer, no
        // second draw node), or static-vs-positioned chains would diverge
        // structurally and invalidate the chain pins in ShadowApplierTest.
        val config = ShadowConfig(
            shadows = listOf(
                ShadowData(offsetX = 20.dp, offsetY = 20.dp, blurRadius = 10.dp, color = Color.Gray)
            )
        )
        val positioned = elementNames(
            ShadowApplier.applyFullShadow(
                Modifier, config, positionOffset = DpOffset(75.dp, 125.dp)
            )
        )
        val static = elementNames(ShadowApplier.applyFullShadow(Modifier, config))
        // Same single-element DrawBehind chain either way.
        assertEquals(static, positioned)
        assertEquals(positioned.joinToString(), 1, positioned.size)
        assertTrue(positioned[0], positioned[0].contains("DrawBehind"))
    }

    @Test
    fun `inset shadow with position offset keeps the drawWithContent element`() {
        // Inset shadows route through drawWithContent (content first, then
        // the clipped negative-space draw); the translate wraps only the
        // shadow half INSIDE the same lambda, so the chain is unchanged.
        val config = ShadowConfig(
            shadows = listOf(
                ShadowData(blurRadius = 6.dp, color = Color.Black, inset = true)
            )
        )
        val positioned = elementNames(
            ShadowApplier.applyFullShadow(
                Modifier, config, positionOffset = DpOffset(10.dp, (-4).dp)
            )
        )
        val static = elementNames(ShadowApplier.applyFullShadow(Modifier, config))
        assertEquals(static, positioned)
        assertEquals(positioned.joinToString(), 1, positioned.size)
        assertTrue(positioned[0], positioned[0].contains("DrawWithContent"))
    }

    @Test
    fun `public applyShadow entry forwards the offset`() {
        // EffectsFacade calls applyShadow (not applyFullShadow) — the
        // 4-arg overload must exist and produce the same chain shape, or
        // the facade threading silently compiles against a default.
        val config = ShadowConfig(
            shadows = listOf(ShadowData(blurRadius = 4.dp, color = Color.Red))
        )
        val names = elementNames(
            ShadowApplier.applyShadow(
                Modifier, config, positionOffset = DpOffset(5.dp, 5.dp)
            )
        )
        assertTrue(names.toString(), names.any { it.contains("DrawBehind") })
    }
}
