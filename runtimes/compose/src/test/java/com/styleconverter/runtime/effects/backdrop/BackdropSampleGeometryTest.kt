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

    // ── the POSITION offset (wave-27 accuracy fix) ────────────────────────
    //
    // The backdrop draw node is installed at StyleApplier step 3, OUTER of
    // step 4 — and step 4 ends with PositionApplier's `absoluteOffset`. So the
    // node's own origin is the element's UN-offset slot while its background
    // and borders (steps 5–6, inner of the offset) paint at the offset one.
    // borderBox folds the offset into the local origin; these pin that it is
    // an origin-only translation and that it composes with the margin bands.

    @Test
    fun `position offset moves the border box origin only`() {
        // backdrop-filter-basic.html: the 100x100 filterbox declares
        // `left: 50px; top: 50px` inside its positioned parent. Before this,
        // the patch was sampled and painted at (0,0) of the draw node — the
        // PARENT colorbox's corner — which is exactly the measured Android
        // failure (one flat magenta square at canvas (26,116) where the ref
        // has green + black + a magenta overlap starting at (76,166)).
        val box = BackdropSampleGeometry.borderBox(
            nodeWidth = 100f, nodeHeight = 100f,
            marginLeftPx = 0f, marginTopPx = 0f,
            marginRightPx = 0f, marginBottomPx = 0f,
            positionOffsetXPx = 50f, positionOffsetYPx = 50f,
        )!!
        assertEquals(50f, box.localLeft, 0f)
        assertEquals(50f, box.localTop, 0f)
        // absoluteOffset translates without resizing — the box keeps its size.
        assertEquals(100f, box.width, 0f)
        assertEquals(100f, box.height, 0f)
    }

    @Test
    fun `position offset adds to the margin bands`() {
        // Both contributions are translations of the painted box inside an
        // unchanged draw node, so they sum: a 10px left margin plus a
        // `left: 30px` inset puts the border box 40px into the node.
        val box = BackdropSampleGeometry.borderBox(
            nodeWidth = 140f, nodeHeight = 90f,
            marginLeftPx = 10f, marginTopPx = 8f,
            marginRightPx = 20f, marginBottomPx = 12f,
            positionOffsetXPx = 30f, positionOffsetYPx = -4f,
        )!!
        assertEquals(40f, box.localLeft, 0f)
        assertEquals(4f, box.localTop, 0f)
        // Size is still node minus BOTH margin bands, offset-independent.
        assertEquals(110f, box.width, 0f)
        assertEquals(70f, box.height, 0f)
    }

    @Test
    fun `a negative position offset moves the box the other way`() {
        // A `right`/`bottom` inset resolves NEGATIVE (PositionConfig.offsetX),
        // and so does a literal `left: -20px`. Unlike a negative MARGIN — which
        // must never grow the box — a negative offset is a real translation and
        // has to be honoured with its sign, or the patch drifts the wrong way.
        val box = BackdropSampleGeometry.borderBox(
            nodeWidth = 80f, nodeHeight = 60f,
            marginLeftPx = 0f, marginTopPx = 0f,
            marginRightPx = 0f, marginBottomPx = 0f,
            positionOffsetXPx = -20f, positionOffsetYPx = -12f,
        )!!
        assertEquals(-20f, box.localLeft, 0f)
        assertEquals(-12f, box.localTop, 0f)
        assertEquals(80f, box.width, 0f)
        assertEquals(60f, box.height, 0f)
    }

    @Test
    fun `a static element is byte-identical to the pre-offset geometry`() {
        // The overwhelming majority of elements. Defaulted parameters mean the
        // committed captures cannot move: same numbers as before the fix.
        val withDefault = BackdropSampleGeometry.borderBox(200f, 100f, 0f, 0f, 0f, 0f)!!
        assertEquals(0f, withDefault.localLeft, 0f)
        assertEquals(0f, withDefault.localTop, 0f)
        assertEquals(200f, withDefault.width, 0f)
        assertEquals(100f, withDefault.height, 0f)
    }

    @Test
    fun `an offset can never turn a valid border box into a refusal`() {
        // The refusal test is on the SIZE, which the offset does not touch —
        // so a huge inset still yields a box (the sample() clamp is what
        // decides an off-canvas box has nothing to read).
        val box = BackdropSampleGeometry.borderBox(
            nodeWidth = 40f, nodeHeight = 40f,
            marginLeftPx = 0f, marginTopPx = 0f,
            marginRightPx = 0f, marginBottomPx = 0f,
            positionOffsetXPx = 5000f, positionOffsetYPx = 5000f,
        )
        assertEquals(40f, box!!.width, 0f)
        assertEquals(5000f, box.localLeft, 0f)
    }
}
