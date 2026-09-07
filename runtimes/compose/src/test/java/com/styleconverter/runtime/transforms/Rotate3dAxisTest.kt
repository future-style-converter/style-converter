package com.styleconverter.runtime.transforms

// retro R1 (findings A11#6 / A11#11) — rotate3d() and the `rotate: x y z
// <angle>` longhand are classified EXACTLY: unit axes keep their wave-48/49
// single-axis routes, a diagonal axis is preserved for the 4x4 route, and
// the §12.2 zero vector is the identity. Corpus wires are VERBATIM from
// tools/titan/runs/wave49-final/sections/css-transforms/per-test-ir/ and
// fidelity/pairwise/pairs-06.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Rotate3dAxisTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun config(vararg props: Pair<String, String>): TransformConfig =
        TransformExtractor.extractTransformConfig(props.map { (t, j) -> t to parse(j) })

    @Test
    fun `unit axes classify to the single-axis functions`() {
        assertEquals(TransformFunction.RotateX(60f), Rotate3dAxis.classify(1f, 0f, 0f, 60f))
        assertEquals(TransformFunction.RotateY(60f), Rotate3dAxis.classify(0f, 1f, 0f, 60f))
        assertEquals(TransformFunction.RotateZ(60f), Rotate3dAxis.classify(0f, 0f, 1f, 60f))
        // Any length along an axis is the same axis (normalisation).
        assertEquals(TransformFunction.RotateZ(60f), Rotate3dAxis.classify(0f, 0f, 5f, 60f))
    }

    @Test
    fun `a negative unit axis flips the angle`() {
        // rotate3d(0,0,−1,θ) = rotateZ(−θ): the old heuristic ("z >= x && z
        // >= y") took the non-negated angle for a negative z component.
        assertEquals(TransformFunction.RotateZ(-60f), Rotate3dAxis.classify(0f, 0f, -1f, 60f))
        assertEquals(TransformFunction.RotateX(-60f), Rotate3dAxis.classify(-1f, 0f, 0f, 60f))
    }

    @Test
    fun `a zero vector is not applied`() {
        // css-transforms-2 §12.2: "a direction vector that cannot be
        // normalized, such as [0, 0, 0], will cause the rotation to not be
        // applied". The old heuristic returned rotateZ(θ).
        assertNull(Rotate3dAxis.classify(0f, 0f, 0f, 90f))
    }

    @Test
    fun `a diagonal axis is kept whole and normalised`() {
        val fn = Rotate3dAxis.classify(1f, 1f, 0f, 45f)
        assertTrue(fn is TransformFunction.Rotate3d)
        fn as TransformFunction.Rotate3d
        assertEquals(0.70711f, fn.x, 1e-4f)
        assertEquals(0.70711f, fn.y, 1e-4f)
        assertEquals(0f, fn.z, 0f)
        assertEquals(45f, fn.degrees, 0f)
    }

    @Test
    fun `corpus rotate3d on a unit axis keeps its wave-49 route`() {
        // VERBATIM: per-test-ir/wpt__css-transforms__css-rotate-2d-3d-001.json —
        // rotate(90deg) rotate3d(1,0,0,60deg). Still RotateX(60), still the
        // ordered composer's flattened Scale step (OrthographicFlattenTest).
        val c = config(
            "Transform" to """{"type":"functions","list":[{"fn":"rotate","a":{"deg":90}},{"fn":"rotate3d","x":1,"y":0,"z":0,"a":{"deg":60}}]}""",
        )
        assertEquals(TransformFunction.RotateX(60f), c.functions[1])
        assertTrue(TransformListComposer.stepsFor(c) != null)
        assertTrue(!TransformMatrixComposer.takesMatrixPath(c))
    }

    @Test
    fun `the diagonal rotate longhand lands in rotate3d, not the planar rotate`() {
        // VERBATIM: pairs-06 PW_Transforms_Typography_01 — `rotate: 1 1 0
        // 45deg` (with `perspective: 0`). The extractor read only x/y and
        // fell to the planar `else`: a Z rotation (0.71–0.81 on Android).
        val c = config(
            "Perspective" to """{"type":"length","px":0}""",
            "Rotate" to """{"type":"axis-angle","x":1,"y":1,"z":0,"angle":{"deg":45}}""",
        )
        assertNull(c.rotate)
        assertNull(c.rotateX)
        assertNull(c.rotateY)
        assertEquals(45f, c.rotate3d!!.degrees, 0f)
        assertTrue(c.has3DTransform)
        assertTrue(TransformMatrixComposer.takesMatrixPath(c))
    }

    @Test
    fun `the axis-aligned rotate longhand still lands on its legacy field`() {
        // VERBATIM: per-test-ir/…/animation/rotate-animation-with-will-change-
        // transform-001 — `rotate: 0 1 0 44deg` (1.0 SSIM on all three).
        val c = config("Rotate" to """{"type":"axis-angle","x":0,"y":1,"z":0,"angle":{"deg":44}}""")
        assertEquals(44f, c.rotateY!!, 0f)
        assertNull(c.rotate3d)
        assertTrue(!TransformMatrixComposer.takesMatrixPath(c))
    }
}
