package com.styleconverter.runtime.effects.backdrop

// Pins "which backdrop pixels does this element see" — the half of the
// two-pass render that decides whether a blurred backdrop matches the ref at
// the canvas edge (filter-effects-2 §2 edge duplication) and whether a
// degenerate box refuses instead of drawing something plausible-but-wrong.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackdropSampleGeometryTest {

    // ── padding ───────────────────────────────────────────────────────────

    @Test
    fun `no blur means no padding`() {
        // An invert-only chain samples exactly the border box.
        assertEquals(0, BackdropSampleGeometry.blurPadPx(0f))
    }

    @Test
    fun `padding is three sigma rounded up`() {
        // 3σ carries >99.7% of the kernel; rounding UP means the sample is
        // never a pixel short of what the Gaussian reads.
        assertEquals(30, BackdropSampleGeometry.blurPadPx(10f))
        assertEquals(8, BackdropSampleGeometry.blurPadPx(2.5f))
    }

    // ── the unpadded (invert) sample ──────────────────────────────────────

    @Test
    fun `interior box samples itself one to one`() {
        // backdrop-filter-basic.html geometry: the 100x100 filterbox at
        // (60,150) over a green backdrop, no blur.
        val s = BackdropSampleGeometry.sample(60, 150, 100, 100, 0, 390, 600)!!
        assertEquals(60, s.srcLeft)
        assertEquals(150, s.srcTop)
        assertEquals(100, s.width)
        assertEquals(100, s.height)
        // Unpadded and fully inside → the crop lands at the box's own origin.
        assertEquals(0, s.dstLeft)
        assertEquals(0, s.dstTop)
    }

    // ── the padded (blur) sample ──────────────────────────────────────────

    @Test
    fun `padded interior sample reaches outside the box`() {
        // The patch is drawn at a NEGATIVE element-local offset and the
        // border-box clip cuts it back — that is how pixels from outside the
        // box feed the Gaussian inside it.
        val s = BackdropSampleGeometry.sample(100, 100, 50, 50, 30, 390, 600)!!
        assertEquals(70, s.srcLeft)
        assertEquals(70, s.srcTop)
        assertEquals(110, s.width) // 50 + 2×30
        assertEquals(110, s.height)
        assertEquals(-30, s.dstLeft)
        assertEquals(-30, s.dstTop)
    }

    @Test
    fun `sample clamps at the backdrop root edges`() {
        // A box hugging the canvas origin: the padded rect would start at
        // (-30,-30), which does not exist. We clamp to the image and let the
        // painter's TileMode_CLAMP duplicate the edge pixels — the spec's
        // edge behaviour, and the reason this returns a SMALLER crop instead
        // of refusing.
        val s = BackdropSampleGeometry.sample(0, 0, 50, 50, 30, 390, 600)!!
        assertEquals(0, s.srcLeft)
        assertEquals(0, s.srcTop)
        assertEquals(80, s.width) // 50 + 30 on the inside edge only
        assertEquals(0, s.dstLeft) // crop origin == box origin again
        assertEquals(0, s.dstTop)
    }

    @Test
    fun `sample clamps at the far edges too`() {
        // 390-wide canvas, box flush against the right/bottom of a 600 image.
        val s = BackdropSampleGeometry.sample(360, 570, 30, 30, 30, 390, 600)!!
        assertEquals(330, s.srcLeft)
        assertEquals(540, s.srcTop)
        assertEquals(60, s.width) // 330..390
        assertEquals(60, s.height) // 540..600
        assertEquals(-30, s.dstLeft)
        assertEquals(-30, s.dstTop)
    }

    // ── refusals ──────────────────────────────────────────────────────────

    @Test
    fun `zero sized element refuses`() {
        // backdrop-filter-zero-size.html — nothing to filter, and the painter
        // must draw nothing rather than a degenerate patch.
        assertNull(BackdropSampleGeometry.sample(10, 10, 0, 40, 0, 390, 600))
        assertNull(BackdropSampleGeometry.sample(10, 10, 40, 0, 0, 390, 600))
    }

    @Test
    fun `empty backdrop refuses`() {
        // A failed / not-yet-published pass A must never be sampled.
        assertNull(BackdropSampleGeometry.sample(10, 10, 40, 40, 0, 0, 0))
    }

    @Test
    fun `fully off canvas element refuses`() {
        // Hoisted/overflowing boxes can sit entirely outside the canvas; the
        // clamped rect is then empty and there is nothing honest to draw.
        assertNull(BackdropSampleGeometry.sample(500, 10, 40, 40, 0, 390, 600))
        assertNull(BackdropSampleGeometry.sample(10, -80, 40, 40, 0, 390, 600))
    }
}
