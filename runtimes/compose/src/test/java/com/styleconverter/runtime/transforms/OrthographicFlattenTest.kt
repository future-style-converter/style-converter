package com.styleconverter.runtime.transforms

// Wave 49 (lane A6) — orthographic flattening of 3D rotations.
//
// Every `Transform` / `Rotate` / `TransformOrigin` JSON string below is a
// VERBATIM payload copied out of the wave-48 corpus per-test IR
// (tools/titan/runs/wave48-final/sections/css-transforms/per-test-ir/…),
// not an invented shape. The expected numbers are shown as arithmetic in
// the comments, the same `_expect` discipline TransformListComposerTest
// follows.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrthographicFlattenTest {

    // Same parse/build idiom as TransformListComposerTest — the payloads
    // travel through the REAL extractor so the wire shapes stay honest.
    private fun parse(s: String) = Json.parseToJsonElement(s)

    private fun config(vararg props: Pair<String, String>): TransformConfig =
        TransformExtractor.extractTransformConfig(props.map { (t, j) -> t to parse(j) })

    // ------------------------------------------------------------------
    // The projection rule itself (css-transforms-2 §4.1).
    // ------------------------------------------------------------------

    @Test
    fun `rotateY flattens to a cos scale on X`() {
        // css-transforms/css-transform-3d-rotateY-positive: a 240x120 box
        // under rotateY(60deg) must project to the ref's 120x120 SQUARE.
        // cos 60° = 0.5, and 240 · 0.5 = 120.
        val f = OrthographicFlatten.of(rotateXDeg = 0f, rotateYDeg = 60f)
        assertEquals(0.5f, f.scaleX, 1e-4f)
        assertEquals(1f, f.scaleY, 1e-4f)
        assertFalse(f.shearDropped)
    }

    @Test
    fun `rotateX flattens to a cos scale on Y`() {
        // css-transforms/css-transform-3d-rotateX-positive, the transpose
        // of the case above: a 120x240 box under rotateX(60deg) → 120x120.
        val f = OrthographicFlatten.of(rotateXDeg = 60f, rotateYDeg = 0f)
        assertEquals(1f, f.scaleX, 1e-4f)
        assertEquals(0.5f, f.scaleY, 1e-4f)
    }

    @Test
    fun `negative angles project identically — cos is even`() {
        // css-transform-3d-rotateY-negative uses -60deg and shares the
        // SAME frozen reference PNG as the +60 case (both 120x120).
        val pos = OrthographicFlatten.of(0f, 60f)
        val neg = OrthographicFlatten.of(0f, -60f)
        assertEquals(pos.scaleX, neg.scaleX, 1e-6f)
    }

    @Test
    fun `a half turn mirrors rather than vanishing`() {
        // cos 180° = -1. CSS shows the MIRRORED back face of a box turned
        // past 90° (hiding it is backface-visibility's job, not the
        // projection's), and a negative graphicsLayer scale is exactly
        // that mirror. Corpus carriers: every backface-visibility-hidden-*
        // and composited-under-rotateY-180deg* test.
        val f = OrthographicFlatten.of(0f, 180f)
        assertEquals(-1f, f.scaleX, 1e-4f)
    }

    @Test
    fun `a quarter turn collapses the box`() {
        // css-transforms/css3-transform-perspective asserts that
        // "rotateX 90 degrees makes it invisible" — cos 90° = 0, so the
        // flattened box has zero height and paints nothing.
        val f = OrthographicFlatten.of(90f, 0f)
        assertTrue(abs(f.scaleY) < 1e-6f)
    }

    // ------------------------------------------------------------------
    // The guards — when the orthographic rule must NOT be used.
    // ------------------------------------------------------------------

    @Test
    fun `a perspective distance disables the orthographic route`() {
        // css-transforms-2 §4.1.1: a perspective puts a real m34 in the
        // matrix, so the projection is projective — and (retro R1) the 4x4
        // canvas route, not a camera, is its owner.
        assertFalse(OrthographicFlatten.appliesTo(perspectivePx = 500f))
        assertTrue(OrthographicFlatten.appliesTo(perspectivePx = 0f))
    }

    @Test
    fun `a z translation without a perspective is orthographic too`() {
        // retro R1 (A11#6): the wave-49 `translateZPx != 0` guard kept a
        // 1000px default camera alive for css-transforms/3d-rendering-
        // context-* (translateZ 10/20 px, no own perspective). css-transforms-2
        // §4.1 drops the z column when no perspective is in effect, so the
        // depth is invisible: appliesTo no longer takes a z argument at all,
        // and the shared depth-scale owner returns exactly 1 for that shape.
        // Measured on 3d-rendering-context-and-abspos: the 1.02x/1.01x
        // oversized green boxes exposed a red/orange edge the frozen ref
        // does not have — removing the scale moves TOWARD the ref.
        assertTrue(OrthographicFlatten.appliesTo(perspectivePx = 0f))
        assertEquals(1f, TransformMatrixComposer.depthScale(perspectivePx = 0f, zPx = 20f), 0f)
        // …while under a real perspective the same z scales by P/(P − z).
        assertEquals(500f / 480f, TransformMatrixComposer.depthScale(perspectivePx = 500f, zPx = 20f), 1e-5f)
    }

    @Test
    fun `single-axis rotations report no dropped shear`() {
        // sin(0°) = 0 kills the product for every single-axis case, and
        // sin(180°) is float dust (1.2e-16) that must NOT be mistaken for
        // a shear.
        assertEquals(0f, OrthographicFlatten.shearResidual(0f, 60f), 0f)
        assertEquals(0f, OrthographicFlatten.shearResidual(180f, 0f), 0f)
        assertEquals(0f, OrthographicFlatten.shearResidual(180f, 180f), 0f)
    }

    @Test
    fun `two simultaneous axes report the dropped shear`() {
        // R_y(β)·R_x(α) flattens to [[cosβ, sinα·sinβ],[0, cosα]]; the
        // top-right term is the shear a scale pair cannot carry.
        // sin45° · sin45° = 0.5.
        assertEquals(0.5f, OrthographicFlatten.shearResidual(45f, 45f), 1e-4f)
        assertTrue(OrthographicFlatten.of(45f, 45f).shearDropped)
    }

    // ------------------------------------------------------------------
    // Routing — which configs the ordered composer may now own.
    // ------------------------------------------------------------------

    @Test
    fun `a bare rotateY list is orthographic-eligible`() {
        // VERBATIM: per-test-ir/wpt__css-transforms__css-transform-3d-
        // transform-style.json, component css-transform-3d-transform-style__1__1.
        val c = config(
            "Transform" to """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":-60}}]}""",
        )
        assertTrue(c.has3DTransform)
        assertTrue(TransformListComposer.isOrthographicRotationOnly(c))
    }

    @Test
    fun `the perspective PROPERTY does not disqualify — only a perspective() FUNCTION does`() {
        // Same list plus the `perspective` PROPERTY — the shape
        // css-transforms/backface-visibility-hidden-001 carries. retro F3:
        // the property is css-transforms-2 §4.1.1's SECOND way, projecting
        // the CHILDREN (§8), so this element's own list stays orthographic
        // (its frozen ref measures 100 rows in every column) and IS eligible.
        // Under R1's fold this pin asserted the opposite; the measurement in
        // TransformMatrixComposer's class doc is why it flipped.
        val prop = config(
            "Transform" to """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":45}}]}""",
            "Perspective" to """{"type":"length","px":1000}""",
        )
        assertTrue(TransformListComposer.isOrthographicRotationOnly(prop))
        // VERBATIM: fidelity/pairwise/pairs-02 → PW_Borders_Transforms_03
        // (`transform: perspective(500px) rotateY(45deg)`): the FUNCTION is
        // §4.1.1's FIRST way and puts the m34 row in THIS element's matrix
        // (§12.2) → refused here; the 4x4 canvas route owns it.
        val fn = config(
            "Transform" to """{"type":"functions","list":[{"fn":"perspective","l":{"px":500.0}},{"fn":"rotateY","a":{"deg":45.0}}]}""",
        )
        assertFalse(TransformListComposer.isOrthographicRotationOnly(fn))
        assertTrue(TransformMatrixComposer.takesMatrixPath(fn))
    }

    @Test
    fun `a translateZ list is not orthographic-eligible`() {
        // VERBATIM: per-test-ir/wpt__css-transforms__3d-rendering-context-
        // and-abspos.json, component 3d-rendering-context-and-abspos__1__0__0.
        val c = config(
            "Transform" to """{"type":"functions","list":[{"fn":"translateZ","z":{"px":20}}]}""",
        )
        assertFalse(TransformListComposer.isOrthographicRotationOnly(c))
        assertNull(TransformListComposer.stepsFor(c))
    }

    // ------------------------------------------------------------------
    // Ordered composition of a mixed 2D/3D list.
    // ------------------------------------------------------------------

    @Test
    fun `rotate then rotate3d composes to the reference square`() {
        // VERBATIM: per-test-ir/wpt__css-transforms__css-rotate-2d-3d-001.json.
        // The test box is 100x200 and the frozen ref is a 100x100 SQUARE:
        // rotate3d(1,0,0,60deg) flattens to scaleY(0.5) → 100x100, then
        // rotate(90deg) turns the square in place. Android rendered a
        // 215x98 smear in wave 48.
        val c = config(
            "Transform" to
                """{"type":"functions","list":[{"fn":"rotate","a":{"deg":90}},{"fn":"rotate3d","x":1,"y":0,"z":0,"a":{"deg":60}}]}""",
        )
        val steps = TransformListComposer.stepsFor(c)
        assertNotNull("mixed 2D/3D list must reach the ordered composer", steps)
        val m = TransformListComposer.linearOf(steps!!)
        // Map the box's own half-extents through the linear part and read
        // the projected bounding box. (50, 100) is the half-diagonal of a
        // 100x200 box; the image half-extents must both be 50 → 100x100.
        val cornerX = abs(m.a * 50f + m.c * 100f)
        val cornerY = abs(m.b * 50f + m.d * 100f)
        assertEquals(50f, cornerX, 1e-3f)
        assertEquals(50f, cornerY, 1e-3f)
    }

    @Test
    fun `list order changes the matrix for a mixed 2D-3D pair`() {
        // The whole point of routing these through the composer: §11 makes
        // `rotate(90deg) rotateX(60deg)` = R·S and `rotateX(60deg)
        // rotate(90deg)` = S·R, which are DIFFERENT matrices. The legacy
        // kind-accumulator collapsed both to R·S.
        val rs = config(
            "Transform" to
                """{"type":"functions","list":[{"fn":"rotate","a":{"deg":90}},{"fn":"rotateX","a":{"deg":60}}]}""",
        )
        val sr = config(
            "Transform" to
                """{"type":"functions","list":[{"fn":"rotateX","a":{"deg":60}},{"fn":"rotate","a":{"deg":90}}]}""",
        )
        val a = TransformListComposer.linearOf(TransformListComposer.stepsFor(rs)!!)
        val b = TransformListComposer.linearOf(TransformListComposer.stepsFor(sr)!!)
        // R(90)·diag(1,0.5) sends (1,0) to (0,1) and (0,1) to (-0.5,0);
        // diag(1,0.5)·R(90) sends (1,0) to (0,0.5) and (0,1) to (-1,0).
        assertEquals(1f, a.b, 1e-4f)
        assertEquals(0.5f, b.b, 1e-4f)
    }
}
