package com.styleconverter.runtime.effects.backdrop

// Pins "which backdrop pixels does this element see" — the half of the
// two-pass render that decides whether a blurred backdrop matches the ref
// (filter-effects-2 §2: the backdrop is clipped to the BORDER BOX before the
// filter runs, so the sample never grows with σ) and whether a degenerate box
// refuses instead of drawing something plausible-but-wrong.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackdropSampleGeometryTest {

    // ── the sample rect IS the border box ────────────────────────────────

    @Test
    fun `interior box samples itself one to one`() {
        // backdrop-filter-basic.html geometry: the 100x100 filterbox at
        // (60,150) over a green backdrop, no blur.
        val s = BackdropSampleGeometry.sample(60, 150, 100, 100, 390, 600)!!
        assertEquals(60, s.srcLeft)
        assertEquals(150, s.srcTop)
        assertEquals(100, s.width)
        assertEquals(100, s.height)
        // Fully inside → the crop lands at the box's own origin.
        assertEquals(0, s.dstLeft)
        assertEquals(0, s.dstTop)
    }

    @Test
    fun `a blurred box samples exactly the same rect`() {
        // The wave-34 edge model: filter-effects-2 §2 clips the backdrop to
        // the border box BEFORE filtering, so no σ widens this rect. The
        // Gaussian's out-of-box appetite is fed by TileMode.MIRROR (Compose)
        // / BackdropBlur.mirrorPad (iOS) instead. Refuting evidence for the
        // wave-26 grow-by-3σ model is in BackdropSampleGeometry's header:
        // backdrop-filter-boundary imported the lime page background that the
        // WPT test explicitly forbids.
        //
        // The function no longer TAKES a sigma, so "same rect for every σ" is
        // structural — this test pins the rect the painter now asks for.
        val s = BackdropSampleGeometry.sample(100, 100, 50, 50, 390, 600)!!
        assertEquals(100, s.srcLeft)
        assertEquals(100, s.srcTop)
        assertEquals(50, s.width)
        assertEquals(50, s.height)
        assertEquals(0, s.dstLeft)
        assertEquals(0, s.dstTop)
    }

    @Test
    fun `a box at the backdrop root edges needs no clamp`() {
        // A box hugging the canvas origin used to be the interesting case
        // (the 3σ grow ran off the plate). With the border-box sample there
        // is nothing outside to clamp, blurred or not.
        val s = BackdropSampleGeometry.sample(0, 0, 50, 50, 390, 600)!!
        assertEquals(0, s.srcLeft)
        assertEquals(0, s.srcTop)
        assertEquals(50, s.width)
        assertEquals(50, s.height)
        assertEquals(0, s.dstLeft)
        assertEquals(0, s.dstTop)
    }

    @Test
    fun `a box flush against the far edges needs no clamp either`() {
        // 390-wide canvas, box ending exactly at the right/bottom of a 600
        // image.
        val s = BackdropSampleGeometry.sample(360, 570, 30, 30, 390, 600)!!
        assertEquals(360, s.srcLeft)
        assertEquals(570, s.srcTop)
        assertEquals(30, s.width)
        assertEquals(30, s.height)
        assertEquals(0, s.dstLeft)
        assertEquals(0, s.dstTop)
    }

    // ── the one remaining clamp: a box that OVERHANGS the canvas ──────────

    @Test
    fun `an overhanging box samples only the strip that exists`() {
        // The border box starts 10px left of the canvas. We read the 30px
        // that exist; dstLeft says the crop sits 10px into the box, and the
        // painter leaves the uncovered strip unpainted rather than smearing
        // it. (The mirror band inside the blur then extends this crop, which
        // is the plate boundary's own edge behaviour.)
        val s = BackdropSampleGeometry.sample(-10, 10, 40, 20, 390, 600)!!
        assertEquals(0, s.srcLeft)
        assertEquals(10, s.srcTop)
        assertEquals(30, s.width)
        assertEquals(20, s.height)
        assertEquals(10, s.dstLeft)
        assertEquals(0, s.dstTop)
    }

    @Test
    fun `an overhanging box's crop is smaller than its border box`() {
        // Compose has no explicit keepRect (the painter's border-box clip does
        // that job); the arithmetic the Swift twin's keepRect states is
        // restated here in the same closed form so the two suites agree.
        val s = BackdropSampleGeometry.sample(-10, 10, 40, 20, 390, 600)!!
        // Border-box origin inside the crop = −dst; intersect with the crop.
        val boxLeft = -s.dstLeft
        val boxTop = -s.dstTop
        assertEquals(0, maxOf(0, boxLeft))
        assertEquals(0, maxOf(0, boxTop))
        assertEquals(30, minOf(s.width, boxLeft + 40) - maxOf(0, boxLeft))
        assertEquals(20, minOf(s.height, boxTop + 20) - maxOf(0, boxTop))
    }

    // ── refusals ──────────────────────────────────────────────────────────

    @Test
    fun `zero sized element refuses`() {
        // backdrop-filter-zero-size.html — nothing to filter, and the painter
        // must draw nothing rather than a degenerate patch.
        assertNull(BackdropSampleGeometry.sample(10, 10, 0, 40, 390, 600))
        assertNull(BackdropSampleGeometry.sample(10, 10, 40, 0, 390, 600))
    }

    @Test
    fun `empty backdrop refuses`() {
        // A failed / not-yet-published pass A must never be sampled.
        assertNull(BackdropSampleGeometry.sample(10, 10, 40, 40, 0, 0))
    }

    @Test
    fun `fully off canvas element refuses`() {
        // Hoisted/overflowing boxes can sit entirely outside the canvas; the
        // clamped rect is then empty and there is nothing honest to draw.
        assertNull(BackdropSampleGeometry.sample(500, 10, 40, 40, 390, 600))
        assertNull(BackdropSampleGeometry.sample(10, -80, 40, 40, 390, 600))
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
