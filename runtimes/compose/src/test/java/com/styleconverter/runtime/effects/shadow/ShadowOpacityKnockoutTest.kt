package com.styleconverter.runtime.effects.shadow

// Retro R6 (audit findings A11#2 + A10#5) — the two css-backgrounds-3 §6.1
// rules the outset box-shadow painter lacked, measured on images.combos
// 003_Images_Decorated (`box-shadow: 0 0 0 3px #3498db; opacity: .55` on
// #e74c3c over the (26,26,46) canvas):
//   1. KNOCKOUT — "The shadow is drawn outside the border edge only: it is
//      clipped inside the border-box of the element." Android painted the
//      Gaussian UNDER the box: fill (150,110,132) = fill@0.55 over the OPAQUE
//      ring, vs iOS/web (139,54,54) = fill@0.55 over the canvas.
//   2. GROUP ALPHA — css-color-4 §3.3 applies `opacity` "to the element as a
//      whole, including its contents": Android ring (52,152,219) at full
//      alpha vs iOS/web (40,95,141).
// The geometry (ShadowGeometry.borderBoxRect) and the alpha
// (ShadowGeometry.attenuate) are pure and pinned here; the Difference clip
// itself is an android.graphics call inside the draw lambda (a throwing stub
// on this JVM), pinned by fixtures/properties/effects/box-shadow-opacity-
// knockout.json on device. Proven able to fail: removing the elementAlpha
// multiply in attenuate fails `attenuate scales alpha`; adding the spread to
// borderBoxRect fails `knockout ignores spread`.

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowOpacityKnockoutTest {

    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // The fixture's palette.
    private val ring = Color(52f / 255f, 152f / 255f, 219f / 255f)   // #3498db
    private val fill = Color(231f / 255f, 76f / 255f, 60f / 255f)    // #e74c3c
    private val canvas = Triple(26f, 26f, 46f)                        // harness page bg

    // Source-over of an (r,g,b) colour at `alpha` onto an opaque backdrop —
    // the arithmetic the fixture's _expect values were derived with.
    private fun over(bg: Triple<Float, Float, Float>, c: Color, alpha: Float): Triple<Float, Float, Float> = Triple(
        bg.first + alpha * (c.red * 255f - bg.first),
        bg.second + alpha * (c.green * 255f - bg.second),
        bg.third + alpha * (c.blue * 255f - bg.third),
    )

    private fun assertRgb(expected: Triple<Int, Int, Int>, actual: Triple<Float, Float, Float>, tol: Float = 1f) {
        assertEquals("r", expected.first.toFloat(), actual.first, tol)
        assertEquals("g", expected.second.toFloat(), actual.second, tol)
        assertEquals("b", expected.third.toFloat(), actual.third, tol)
    }

    // ── knockout geometry ─────────────────────────────────────────────────

    @Test
    fun `knockout is the border box translated by the position offset only`() {
        val rect = ShadowGeometry.borderBoxRect(120f, 60f)
        assertEquals(0f, rect.left, 0f); assertEquals(0f, rect.top, 0f)
        assertEquals(120f, rect.right, 0f); assertEquals(60f, rect.bottom, 0f)
        // A positioned element's box lives at the offset slot (the wave-27
        // geometry) — the knockout must follow it exactly.
        val positioned = ShadowGeometry.borderBoxRect(100f, 100f, positionOffsetXPx = 75f, positionOffsetYPx = 125f)
        assertEquals(75f, positioned.left, 0f); assertEquals(125f, positioned.top, 0f)
        assertEquals(175f, positioned.right, 0f); assertEquals(225f, positioned.bottom, 0f)
    }

    @Test
    fun `knockout equals the shadow perimeter at zero offset and zero spread`() {
        // The two rects share one origin story (the box at the offset slot);
        // with no shadow offset and no spread they must coincide.
        val box = ShadowGeometry.borderBoxRect(120f, 60f, 10f, 20f)
        val perimeter = ShadowGeometry.outsetShadowRect(120f, 60f, 0f, 0f, 0f, 10f, 20f)
        assertEquals(perimeter, box)
    }

    @Test
    fun `knockout ignores spread and shadow offset`() {
        // §6.1: the spread inflates the SHADOW's perimeter, the shadow offset
        // moves the SHADOW — neither touches the box that occludes it.
        val box = ShadowGeometry.borderBoxRect(120f, 60f)
        val spread = ShadowGeometry.outsetShadowRect(120f, 60f, 0f, 0f, spreadPx = 3f)
        val offset = ShadowGeometry.outsetShadowRect(120f, 60f, 20f, 20f, spreadPx = 0f)
        assertNotEquals(spread, box)
        assertNotEquals(offset, box)
        assertEquals(126f, spread.right - spread.left, 0f)   // the ring's outer extent
        assertEquals(120f, box.right - box.left, 0f)         // the box, unchanged
    }

    // ── group alpha ───────────────────────────────────────────────────────

    @Test
    fun `attenuate scales alpha by the element opacity and keeps the channels`() {
        val a = ShadowGeometry.attenuate(ring, 0.55f)
        assertEquals(0.55f, a.alpha, 0.002f)
        assertEquals(ring.red, a.red, 0.002f)
        assertEquals(ring.green, a.green, 0.002f)
        assertEquals(ring.blue, a.blue, 0.002f)
        // A translucent declared colour compounds: rgba(...,0.5) × 0.55.
        assertEquals(0.275f, ShadowGeometry.attenuate(ring.copy(alpha = 0.5f), 0.55f).alpha, 0.004f)
    }

    @Test
    fun `attenuate is the identity at opacity 1 and clamps out-of-range opacity`() {
        assertEquals(ring, ShadowGeometry.attenuate(ring, 1f))
        // css-color-4 §3.3 computed-value clamp to [0,1].
        assertEquals(1f, ShadowGeometry.attenuate(ring, 1.5f).alpha, 0f)
        assertEquals(0f, ShadowGeometry.attenuate(ring, -0.5f).alpha, 0f)
    }

    @Test
    fun `fixture arithmetic - ring and fill composite to the measured iOS and web values`() {
        // The spec render (ring at 0.55 over the canvas, no fill under it
        // thanks to the knockout): (40.3, 95.3, 141.15).
        assertRgb(Triple(40, 95, 141), over(canvas, ring, ShadowGeometry.attenuate(ring, 0.55f).alpha))
        // The fill inside the box at 0.55 over the canvas: (138.75, 53.5, 53.7).
        assertRgb(Triple(139, 54, 54), over(canvas, fill, 0.55f))
        // The DIAGNOSED wrong render: fill at 0.55 over the opaque ring —
        // what a shadow painted UNDER the box produces — (150.45,110.2,131.55),
        // exactly the pngtool readback of the pre-fix Android capture.
        val opaqueRing = Triple(ring.red * 255f, ring.green * 255f, ring.blue * 255f)
        assertRgb(Triple(150, 110, 132), over(opaqueRing, fill, 0.55f))
        // And the escaped-group ring: the declared colour at full alpha.
        assertRgb(Triple(52, 152, 219), over(canvas, ring, 1f))
    }

    // ── chain shape ───────────────────────────────────────────────────────

    @Test
    fun `opacity threading keeps the single DrawBehind element`() {
        // The knockout clip and the alpha live INSIDE the existing drawBehind
        // lambda — no new node, so ShadowApplierTest's chain pins hold and
        // static-vs-translucent chains stay structurally identical.
        val cfg = ShadowConfig(shadows = listOf(ShadowData(spreadRadius = 3.dp, color = ring)))
        val translucent = elementNames(
            ShadowApplier.applyShadow(Modifier, cfg, positionOffset = DpOffset(6.dp, 4.dp), elementAlpha = 0.55f)
        )
        val opaque = elementNames(ShadowApplier.applyShadow(Modifier, cfg))
        assertEquals(opaque, translucent)
        assertEquals(translucent.joinToString(), 1, translucent.size)
        assertTrue(translucent[0], translucent[0].contains("DrawBehind"))
    }
}
