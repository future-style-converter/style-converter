package com.styleconverter.runtime.effects.filter

// Retro R6 (audit finding A12#2) — pins the layer sizing `filter: blur()`
// records into. The defect: Modifier.blur's RenderNode was sized to the
// node's own bounds, so HWUI's RenderEffect buffer cropped every descendant
// pixel outside the element before blurring (Filter_Blur: the overflowing
// label present on iOS/web, absent on Android). The fix sizes the layer to
// the canvas' current clip ∪ the node box, inflated by 3σ — the same "covers
// the current clip" contract OpacityApplier's saveLayerAlpha(null) gives the
// `opacity` property. Pure geometry, JVM-only.
//
// Proven able to fail: dropping the `pad` term from layerBounds fails the
// first two tests (widths 400/80 instead of 418/98); dropping the node union
// fails `clip smaller than the node`; dropping the maxDim guard fails
// `oversize clip`.

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurLayerGeometryTest {

    @Test
    fun `inflation is three sigma rounded up to whole pixels`() {
        // 3σ holds 99.7 % of the Gaussian's mass; whole pixels keep the
        // layer origin on the lattice (no resampling of the blurred layer).
        assertEquals(9f, BlurLayerGeometry.inflation(3f), 0f)
        assertEquals(2f, BlurLayerGeometry.inflation(0.5f), 0f)   // ceil(1.5)
        assertEquals(0f, BlurLayerGeometry.inflation(0f), 0f)
        // A negative σ cannot shrink the layer.
        assertEquals(0f, BlurLayerGeometry.inflation(-1f), 0f)
    }

    @Test
    fun `layer covers the clip union the node inflated by the gaussian reach`() {
        // The Filter_Blur shape: an 80×40 node whose label overflows right;
        // the root canvas clip seen from the node is (-100,-50)..(300,200).
        val b = BlurLayerGeometry.layerBounds(80f, 40f, Rect(-100f, -50f, 300f, 200f), sigmaPx = 3f)
        assertEquals(-109f, b.left, 0f)          // clip.left − 9
        assertEquals(-59f, b.top, 0f)            // clip.top − 9
        assertEquals(418, b.width)               // (300 + 9) − (−109)
        assertEquals(268, b.height)              // (200 + 9) − (−59)
        assertTrue("a readable clip must be covered", b.coversClip)
    }

    @Test
    fun `a clip smaller than the node never shrinks the layer below the node`() {
        // An ancestor clip cutting into the node: the union keeps the whole
        // node box so the box's own edge still blurs correctly; the ancestor
        // clip (already on the canvas) crops the composite afterwards.
        val b = BlurLayerGeometry.layerBounds(80f, 40f, Rect(10f, 10f, 50f, 30f), sigmaPx = 3f)
        assertEquals(-9f, b.left, 0f)
        assertEquals(-9f, b.top, 0f)
        assertEquals(98, b.width)                // 80 + 2·9
        assertEquals(58, b.height)               // 40 + 2·9
        assertTrue(b.coversClip)
    }

    @Test
    fun `no readable clip falls back to the inflated node box and says so`() {
        // getClipBounds returned false (or an empty rect): nothing to cover
        // beyond the node — but the fallback must be REPORTED (the applier
        // logs it), never silently taken.
        for (clip in listOf<Rect?>(null, Rect.Zero, Rect(5f, 5f, 5f, 40f))) {
            val b = BlurLayerGeometry.layerBounds(80f, 40f, clip, sigmaPx = 3f)
            assertEquals("clip=$clip", -9f, b.left, 0f)
            assertEquals("clip=$clip", -9f, b.top, 0f)
            assertEquals("clip=$clip", 98, b.width)
            assertEquals("clip=$clip", 58, b.height)
            assertFalse("clip=$clip must report the fallback", b.coversClip)
        }
    }

    @Test
    fun `oversize clip falls back to the node box`() {
        // A clip wider than the GPU texture guard: covering it would fail to
        // allocate, so the node box wins and the fallback is reported.
        val b = BlurLayerGeometry.layerBounds(80f, 40f, Rect(0f, 0f, 20000f, 100f), sigmaPx = 3f)
        assertEquals(98, b.width)
        assertEquals(58, b.height)
        assertFalse(b.coversClip)
        // Just under the guard is still covered.
        val ok = BlurLayerGeometry.layerBounds(80f, 40f, Rect(0f, 0f, 8000f, 100f), sigmaPx = 3f)
        assertTrue(ok.coversClip)
        // left = floor(0 − 9) = −9, right = ceil(8000 + 9) = 8009 → 8018 wide,
        // still under the 8192 guard.
        assertEquals(-9f, ok.left, 0f)
        assertEquals(8018, ok.width)
    }

    @Test
    fun `fractional edges snap outward to whole pixels`() {
        // Sub-pixel node sizes / clip edges must never produce a fractional
        // layer origin (a fractional translate would resample the blur).
        val b = BlurLayerGeometry.layerBounds(80.5f, 40.25f, Rect(-0.5f, 0f, 100.25f, 50f), sigmaPx = 1f)
        // pad = ceil(3) = 3: left floor(−0.5 − 3) = −4, top floor(0 − 3) = −3,
        // right ceil(100.25 + 3) = 104, bottom ceil(50 + 3) = 53.
        assertEquals(-4f, b.left, 0f)
        assertEquals(-3f, b.top, 0f)
        assertEquals(108, b.width)
        assertEquals(56, b.height)
    }
}
