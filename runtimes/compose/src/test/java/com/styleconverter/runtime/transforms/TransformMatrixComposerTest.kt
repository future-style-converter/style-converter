package com.styleconverter.runtime.transforms

// retro R1 — the routing gate and the §6 matrix, pinned on VERBATIM wires.
// Every JSON string below is copied unedited from the audit's IR payloads
// (scratchpad retro/A11/runs/*/ir.json, themselves `:converter:run --to ir`
// output of the named fixture) or from tools/titan/runs/wave49-final/
// sections/<sec>/per-test-ir/. Findings closed: A11#6 (true perspective
// projection; no depth scale without a perspective), A10#1 (one P/(P − z)
// owner), A11#11 (individual properties in css-transforms-2 §6 order).
// retro F3 (skeptic S2, should-fix): the own `perspective` PROPERTY no
// longer folds into the element's own matrix — it is the CHILDREN's
// (css-transforms-2 §8; §4.1.1 second way). The property-form pins below
// are re-derived to the orthographic answer; the keystone numbers R1 pinned
// survive in the FUNCTION form (transform-functions.json Transform_Perspective).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformMatrixComposerTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    // Through the REAL extractor, so the wire shapes stay honest.
    private fun config(vararg props: Pair<String, String>): TransformConfig =
        TransformExtractor.extractTransformConfig(props.map { (t, j) -> t to parse(j) })

    // ------------------------------------------------------------------
    // A10#1 — the depth scale has exactly one owner and one formula.
    // ------------------------------------------------------------------

    @Test
    fun `depthScale is P over P minus z — the STATUS measurement table`() {
        // docs/STATUS.md "perspective + translateZ was wrong on both natives":
        // 60x20 box under perspective(500px), web exact on every row.
        assertEquals(1.25f, TransformMatrixComposer.depthScale(500f, 100f), 1e-4f)     // 500/400
        assertEquals(500f / 334f, TransformMatrixComposer.depthScale(500f, 166f), 1e-4f) // 1.497
        assertEquals(2f, TransformMatrixComposer.depthScale(500f, 250f), 1e-4f)        // 500/250 — Taylor gave 1.5
        assertEquals(0.5f, TransformMatrixComposer.depthScale(500f, -500f), 1e-4f)     // 500/1000 — Taylor gave 0 → clamp 0.1
        // z ≥ P: at or behind the camera — capped, never divided by zero.
        assertEquals(10f, TransformMatrixComposer.depthScale(500f, 600f), 0f)
        // No depth, or no perspective in effect (§4.1) → identity.
        assertEquals(1f, TransformMatrixComposer.depthScale(500f, 0f), 0f)
        assertEquals(1f, TransformMatrixComposer.depthScale(0f, 30f), 0f)
    }

    @Test
    fun `perspective-translatez fixture stays on graphicsLayer and doubles at z = P over 2`() {
        // VERBATIM: combinations/perspective-translatez.json → PTZ_Z250.
        val c = config(
            "Transform" to """{"type":"functions","list":[{"fn":"perspective","l":{"px":500}},{"fn":"translateZ","z":{"px":250}}]}""",
        )
        // Depth with NO rotation: graphicsLayer's uniform scale is exact, so
        // the passing fixture (15/0/0 in wave 49) keeps its route.
        assertFalse(TransformMatrixComposer.takesMatrixPath(c))
        assertTrue(TransformMatrixComposer.perspectiveInEffect(c))
        // …and the 4x4 agrees with the scalar route: about pivot (30,10) the
        // far corner (60,20) lands at (30,10) + 2·(30,10) = (90,30).
        val (x, y) = TransformMatrixComposer.ctm(c, 1f, 60f, 20f, 30f, 10f).project(60f, 20f)
        assertEquals(90f, x, 1e-3f)
        assertEquals(30f, y, 1e-3f)
    }

    // ------------------------------------------------------------------
    // A11#6 — a perspective meeting a 3D rotation is a true projection.
    // ------------------------------------------------------------------

    @Test
    fun `Perspective_500px PROPERTY with rotateY 30deg is orthographic — the property is the children's`() {
        // VERBATIM: fixtures/properties/transforms/perspective.json → Perspective_500px.
        // css-transforms-2 §4.1.1: the `perspective` PROPERTY is the SECOND
        // way — it projects the element's CHILDREN (§8) — so this element's
        // own list is flattened orthographically (§4.1): rotateY(30°) on the
        // z = 0 plane IS scaleX(cos 30°). retro F3 retired R1's fold here;
        // the committed wave-49 Android baseline for the same shape
        // (visual-test.json Perspective_Rotate) is this flat box. Web and
        // iOS still fold and keystone it — a cross-engine seam, reported.
        val c = config(
            "Perspective" to """{"type":"length","px":500}""",
            "Transform" to """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":30}}]}""",
        )
        assertNotNull(c.perspective)                       // extracted — kept for the children channel
        assertFalse(TransformMatrixComposer.perspectiveInEffect(c))
        assertFalse(TransformMatrixComposer.takesMatrixPath(c))
        assertTrue(TransformListComposer.isOrthographicRotationOnly(c))
        // 120x80 box, pivot (60,40), density 1: x' = 60 + (x − 60)·cos30 —
        // right edge 120 → 60 + 60·0.86603 = 111.962, left edge 0 → 8.038;
        // y is untouched (0 stays 0, 80 stays 80): no keystone.
        val m = TransformMatrixComposer.ctm(c, 1f, 120f, 80f, 60f, 40f)
        assertTrue(m.isAffineOnPlane())
        val (rx, ry) = m.project(120f, 0f)
        assertEquals(111.962f, rx, 0.01f)
        assertEquals(0f, ry, 1e-3f)
        val (lx, ly) = m.project(0f, 80f)
        assertEquals(8.038f, lx, 0.01f)
        assertEquals(80f, ly, 1e-3f)
    }

    @Test
    fun `Transform_Perspective FUNCTION with rotateY 30deg takes the 4x4 route and keystones`() {
        // VERBATIM: fixtures/properties/transforms/transform-functions.json →
        // Transform_Perspective (`transform: perspective(500px) rotateY(30deg)`,
        // 120x80). The FUNCTION is css-transforms-2 §4.1.1's FIRST way — it
        // "computes into the element's current transformation matrix" (§12.2)
        // — so this is the shape A11#6 named: web/iOS draw the trapezoid,
        // the graphicsLayer camera drew a flat rectangle.
        val c = config(
            "Transform" to """{"type":"functions","list":[{"fn":"perspective","l":{"px":500.0}},{"fn":"rotateY","a":{"deg":30.0}}]}""",
        )
        assertTrue(TransformMatrixComposer.perspectiveInEffect(c))
        assertTrue(TransformMatrixComposer.takesMatrixPath(c))
        // Pivot at the centre (60,40), density 1. Right edge, rel (60,−40):
        // Ry(30°) → x₃ = 60·cos30 = 51.9615, z₃ = −60·sin30 = −30; w = 1 −
        // z₃/500 = 1.06 → X = 60 + 51.9615/1.06 = 109.020, Y = 40 − 40/1.06 =
        // 2.264. Left edge, rel (−60,−40): z₃ = +30 → w = 0.94 → X = 60 −
        // 55.278 = 4.722, Y = 40 − 42.553 = −2.553. The near (left) edge is
        // taller — the keystone graphicsLayer never drew.
        val m = TransformMatrixComposer.ctm(c, 1f, 120f, 80f, 60f, 40f)
        assertFalse(m.isAffineOnPlane())
        val (rx, ry) = m.project(120f, 0f)
        assertEquals(109.020f, rx, 0.01f)
        assertEquals(2.264f, ry, 0.01f)
        val (lx, ly) = m.project(0f, 0f)
        assertEquals(4.722f, lx, 0.01f)
        assertEquals(-2.553f, ly, 0.01f)
    }

    @Test
    fun `backface-visibility-hidden-001 renders orthographically — the fold R1 added would have keystoned the ref`() {
        // VERBATIM: tools/titan/runs/wave49-final/sections/css-transforms/
        // per-test-ir/wpt__css-transforms__backface-visibility-hidden-001.json,
        // component __1 (`perspective: 1000px; transform: rotateY(45deg)`,
        // 200x200; its green child is abspos 50,50 100x100).
        val c = config(
            "Width" to """{"type":"length","px":200}""",
            "Height" to """{"type":"length","px":200}""",
            "Perspective" to """{"type":"length","px":1000}""",
            "Transform" to """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":45}}]}""",
        )
        // Routing: not projective (no perspective() FUNCTION), single-axis
        // orthographic → the single-function churn guard keeps it on the
        // graphicsLayer accumulate route (applyTransformFunctions), exactly
        // the wave-49 route that already matched the ref.
        assertFalse(TransformMatrixComposer.perspectiveInEffect(c))
        assertFalse(TransformMatrixComposer.takesMatrixPath(c))
        assertTrue(TransformListComposer.isOrthographicRotationOnly(c))
        assertNull(TransformListComposer.stepsFor(c))
        // The §6 product about the centre (100,100) is affine on the plane:
        // the child's columns x 50..150 map to 100 + (x − 100)·cos45 =
        // 64.645..135.355 (70.7 columns; the frozen ref shows 70) and its
        // rows 50..150 are untouched (100 rows — the ref draws exactly 100 in
        // every one of the 70 columns; S2/measure.py, n = 7000 both).
        val m = TransformMatrixComposer.ctm(c, 1f, 200f, 200f, 100f, 100f)
        assertTrue(m.isAffineOnPlane())
        assertEquals(64.645f, m.project(50f, 50f).first, 0.01f)
        assertEquals(50f, m.project(50f, 50f).second, 1e-3f)
        assertEquals(135.355f, m.project(150f, 150f).first, 0.01f)
        assertEquals(150f, m.project(150f, 150f).second, 1e-3f)
        // The shape this pin refuses, built explicitly: R1's fold multiplied
        // T(pivot)·P(1000)·Ry(45)·T(−pivot). Left edge rel (−50,−50): z₃ =
        // +50·sin45 = 35.355, w = 1 − 35.355/1000 = 0.96464 → Y = 100 −
        // 50/0.96464 = 48.17 (bottom 151.83: 103.7 rows); right edge rel
        // (50,−50): w = 1.03536 → Y = 100 − 48.29 = 51.71 (96.6 rows) — the
        // keystone S2's specmat.py predicted and the ref does not have. If
        // the fold ever returns to ctm, the affine assertions above fail.
        val folded = Mat4.translate(100f, 100f, 0f) * Mat4.perspective(1000f) *
            Mat4.rotate3d(0f, 1f, 0f, 45f) * Mat4.translate(-100f, -100f, 0f)
        assertFalse(folded.isAffineOnPlane())
        assertEquals(48.17f, folded.project(50f, 50f).second, 0.01f)
        assertEquals(51.71f, folded.project(150f, 50f).second, 0.01f)
    }

    @Test
    fun `Perspective_None with rotateY stays orthographic and off the 4x4 route`() {
        // VERBATIM: perspective.json → Perspective_None. `none` extracts as a
        // null distance; the single-function list keeps its wave-49 route.
        val c = config(
            "Perspective" to """{"type":"none"}""",
            "Transform" to """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":30}}]}""",
        )
        assertNull(c.perspective)
        assertFalse(TransformMatrixComposer.perspectiveInEffect(c))
        assertFalse(TransformMatrixComposer.takesMatrixPath(c))
    }

    @Test
    fun `Perspective_Zero PROPERTY stays off the own matrix — the 1px clamp is the children's`() {
        // VERBATIM: perspective.json → Perspective_Zero. tools/wpt/css/
        // css-transforms/perspective-zero-2.html asserts "'perspective: 0px'
        // behaves the same as perspective: 1px" (csswg-drafts #413) — a
        // statement about the CHILDREN's projection (§8), which the extracted
        // 0 still carries and the clamp still pins. The element's OWN list is
        // orthographic regardless (§4.1.1 second way; retro F3): 0 is not
        // "no perspective", it is simply not this element's perspective.
        val c = config(
            "Perspective" to """{"type":"length","px":0}""",
            "Transform" to """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":30}}]}""",
        )
        val p = c.perspective                                // smart-cast target: the extracted 0px length
        assertEquals(0f, p!!.value, 0f)                      // extracted as a length, NOT as `none`
        assertEquals(1f, TransformMatrixComposer.clampPerspectivePx(p.value), 0f)
        assertFalse(TransformMatrixComposer.perspectiveInEffect(c))
        assertFalse(TransformMatrixComposer.takesMatrixPath(c))
        assertTrue(TransformMatrixComposer.ctm(c, 1f, 120f, 80f, 60f, 40f).isAffineOnPlane())
    }

    @Test
    fun `translate longhand with a z component and perspective none has no depth`() {
        // VERBATIM: fidelity/pairwise/pairs-04 → PW_Images_Transforms_01
        // (`translate: 10px 20px 30px; perspective: none; transform:
        // translateX(20px)`). Android drew 184x93 for a 180x92 box: the 30px
        // z was scaled by a 1000px default camera CSS never has (§4.1).
        val c = config(
            "Translate" to """{"type":"3d","x":{"type":"length","px":10},"y":{"type":"length","px":20},"z":{"px":30}}""",
            "Perspective" to """{"type":"none"}""",
            "Transform" to """{"type":"functions","list":[{"fn":"translateX","x":{"px":20}}]}""",
        )
        val tz = c.translateZ                          // smart-cast target: the 3d wire's z axis
        assertEquals(30f, tz!!.value, 0f)
        assertFalse(TransformMatrixComposer.takesMatrixPath(c))
        assertFalse(TransformMatrixComposer.perspectiveInEffect(c))   // `none`, and no perspective() function
        assertEquals(1f, TransformMatrixComposer.depthScale(0f, tz.value), 0f)
        // The 4x4 says the same: the plane maps affinely, shifted by 10+20 = 30 px in x.
        val m = TransformMatrixComposer.ctm(c, 1f, 180f, 92f, 90f, 46f)
        assertTrue(m.isAffineOnPlane())
        assertEquals(30f, m.toAffine2D().tx, 1e-3f)
        assertEquals(20f, m.toAffine2D().ty, 1e-3f)
    }

    @Test
    fun `matrix3d takes the 4x4 route and is read column-major`() {
        // VERBATIM: per-test-ir/wpt__css-transforms__backface-visibility-
        // hidden-animated-001.json — matrix3d(−1,0,0,0, 0,1,0,0, 0,0,−1,0,
        // 0,0,0,1) = scale3d(−1, 1, −1): the back face, flattened to scaleX(−1).
        // The old matrix route decomposed only 2D matrices and skipped this.
        val c = config(
            "Transform" to """{"type":"functions","list":[{"fn":"matrix3d","a1":-1,"b1":0,"c1":0,"d1":0,"a2":0,"b2":1,"c2":0,"d2":0,"a3":0,"b3":0,"c3":-1,"d3":0,"a4":0,"b4":0,"c4":0,"d4":1}]}""",
        )
        assertTrue(c.has3DTransform)
        assertTrue(TransformMatrixComposer.takesMatrixPath(c))
        val a = TransformMatrixComposer.ctm(c, 1f, 200f, 200f, 0f, 0f).toAffine2D()
        assertEquals(-1f, a.a, 1e-6f)
        assertEquals(1f, a.d, 1e-6f)
        assertEquals(0f, a.b, 1e-6f)
    }

    // ------------------------------------------------------------------
    // A11#11 — individual properties compose in css-transforms-2 §6 order.
    // ------------------------------------------------------------------

    @Test
    fun `pairs-01 038 takes the ordered route — scale longhand OUTSIDE the rotateZ list`() {
        // VERBATIM: fidelity/pairwise/pairs-01 → PW_Background_Transforms_03
        // (`perspective: 500px; transform: rotateZ(45deg); scale: 1.2 0.8`).
        // The `perspective` property disqualified the ordered route (any
        // perspective → "projective"), so the legacy accumulator drew R·S
        // where §6 step 5 then step 7 is S·R — the band Android rendered
        // visibly thicker (0.8261/0.8245 vs iOS-web 0.9903).
        val c = config(
            "Perspective" to """{"type":"length","px":500}""",
            "Transform" to """{"type":"functions","list":[{"fn":"rotateZ","a":{"deg":45}}]}""",
            "Scale" to """{"type":"2d","x":1.2,"y":0.8}""",
        )
        // retro F3: the property is the CHILDREN's (§8) and no route reads
        // it, so the ordered-route decision rests on the planar list alone —
        // it no longer depends on any fold rule (R1's depth-bearing gate
        // happened to give the same answer here because rotateZ is planar).
        assertFalse(TransformMatrixComposer.perspectiveInEffect(c))
        assertFalse(TransformMatrixComposer.takesMatrixPath(c))
        assertTrue(TransformMatrixComposer.ctm(c, 1f, 100f, 100f, 50f, 50f).isAffineOnPlane())
        val steps = TransformListComposer.stepsFor(c)
        assertNotNull("perspective property must not veto a planar list", steps)
        assertEquals(
            listOf(TransformListComposer.Step.Scale(1.2f, 0.8f), TransformListComposer.Step.Rotate(45f)),
            steps,
        )
        // S·R: b = 0.8·sin45° = 0.5657. The legacy R·S had b = 1.2·sin45° = 0.8485.
        val m = TransformListComposer.linearOf(steps!!)
        assertEquals(0.8f * 0.70711f, m.b, 1e-4f)
        assertEquals(1.2f * 0.70711f, m.a, 1e-4f)
    }

    @Test
    fun `pairs-06 rotate 1 1 0 45deg longhand takes the 4x4 route without the own perspective`() {
        // VERBATIM: fidelity/pairwise/pairs-06 → PW_Spacing_Transforms_04
        // (`perspective: 1000px; transform-origin: 50% 50%; rotate: 1 1 0 45deg`).
        // The longhand used to fall to a planar Z rotation (0.71–0.81 on
        // Android); and the own `perspective` never reaches a longhand (web:
        // no `transform` string to prefix; iOS: "NO perspective for the
        // longhand `rotate`"), so the projection is orthographic.
        val c = config(
            "Perspective" to """{"type":"length","px":1000}""",
            "TransformOrigin" to """{"x":{"type":"percentage","percentage":50},"y":{"type":"percentage","percentage":50}}""",
            "Rotate" to """{"type":"axis-angle","x":1,"y":1,"z":0,"angle":{"deg":45}}""",
        )
        assertNull(c.rotate)
        assertNotNull(c.rotate3d)
        assertTrue(TransformMatrixComposer.takesMatrixPath(c))
        assertFalse(TransformMatrixComposer.perspectiveInEffect(c))
        val m = TransformMatrixComposer.ctm(c, 1f, 140f, 48f, 70f, 24f)
        assertTrue(m.isAffineOnPlane())
        assertEquals(0.85355f, m.toAffine2D().a, 1e-4f)                // the symmetric squeeze
        assertEquals(0.14645f, m.toAffine2D().b, 1e-4f)
    }

    @Test
    fun `ctm applies translate then rotate then scale longhands before the list`() {
        // css-transforms-2 §6 steps 3–5 then 7, on a synthetic config built
        // from verbatim wire SHAPES: `translate: 10px 0`, `rotate: 90deg`,
        // `scale: 2`, `transform: translateX(5px)`. Reading the product
        // right-to-left the list maps the point first: (0,0) → (5,0) → scale
        // → (10,0) → rotate 90° cw → (0,10) → translate → (10,10).
        val c = config(
            "Translate" to """{"type":"2d","x":{"type":"length","px":10},"y":{"type":"length","px":0}}""",
            "Rotate" to """{"type":"angle","deg":90}""",
            "Scale" to """{"type":"uniform","value":2}""",
            "Transform" to """{"type":"functions","list":[{"fn":"translateX","x":{"px":5}}]}""",
        )
        val (x, y) = TransformMatrixComposer.ctm(c, 1f, 10f, 10f, 0f, 0f).project(0f, 0f)
        assertEquals(10f, x, 1e-4f)
        assertEquals(10f, y, 1e-4f)
    }
}
