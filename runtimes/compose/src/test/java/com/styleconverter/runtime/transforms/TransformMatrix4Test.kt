package com.styleconverter.runtime.transforms

// retro R1 (findings A11#6 / A10#1) — pins for the 4x4 math in Mat4, the
// single source every exact route now reads. Pure JVM: no Compose scope is
// needed because Mat4 deliberately has no Compose imports. Expected numbers
// are shown as arithmetic in the comments (the _expect discipline of
// fixtures/combinations/*.json), never copied from a render.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TransformMatrix4Test {

    // Degrees → the (cos, sin) pair, so the expectations read as trig.
    private fun cs(deg: Double) = Pair(cos(Math.toRadians(deg)).toFloat(), sin(Math.toRadians(deg)).toFloat())

    @Test
    fun `rotate3d about z is the same clockwise-positive rotation the 2D composer uses`() {
        // css-transforms-2 §16: rotate(α) = rotate3d(0,0,1,α). CSS angles are
        // clockwise in the y-down frame, so (1,0) under 90° lands on (0,1) —
        // the convention TransformListComposer.rotation already renders.
        val (x, y) = Mat4.rotate3d(0f, 0f, 1f, 90f).project(1f, 0f)
        assertEquals(0f, x, 1e-6f)
        assertEquals(1f, y, 1e-6f)
        val planar = TransformListComposer.rotation(90f)              // [[c,-s],[s,c]] with c=0, s=1
        assertEquals(planar.b, y, 1e-6f)                              // image of (1,0) is (a, b)
    }

    @Test
    fun `perspective then translateZ scales the plane by P over P minus z`() {
        // css-transforms-2 §16: perspective(d) has m32 = −1/d, so a point at
        // depth z gets w = 1 − z/d. perspective(500px) translateZ(250px):
        // w = 0.5 → every plane point doubles: (60,20) → (120,40). That is
        // the 2.00 row of the STATUS.md measurement table (the Taylor form
        // 1 + z/P gave 1.50 there — finding A10#1's surviving copy).
        val m = Mat4.perspective(500f) * Mat4.translate(0f, 0f, 250f)
        val (x, y) = m.project(60f, 20f)
        assertEquals(120f, x, 1e-3f)
        assertEquals(40f, y, 1e-3f)
        // The plane is NOT affine any more: m33 = 1 − 250/500 = 0.5 ≠ 1.
        assertFalse(m.isAffineOnPlane())
        // −500px: w = 2 → half size, the row the 0.1 clamp used to swallow.
        val (hx, hy) = (Mat4.perspective(500f) * Mat4.translate(0f, 0f, -500f)).project(60f, 20f)
        assertEquals(30f, hx, 1e-3f)
        assertEquals(10f, hy, 1e-3f)
    }

    @Test
    fun `without a perspective translateZ drops out with the z column`() {
        // css-transforms-2 §4.1: no m34 → flatten by dropping the z row and
        // column. A bare translateZ(30px) is then the identity on the plane
        // (the 1000px default camera that scaled it had no spec basis —
        // pairs-04 040 drew 184x93 for a 180x92 box).
        val m = Mat4.translate(0f, 0f, 30f)
        assertTrue(m.isAffineOnPlane())
        val a = m.toAffine2D()
        assertEquals(TransformListComposer.Affine2D.IDENTITY, a)
    }

    @Test
    fun `matrix3d is read column-major`() {
        // css-transforms-2 §12.2: "matrix3d(a1, b1, c1, d1, a2, …, d4) …
        // 16 values in column-major order". A pure translate(20px, 10px) is
        // therefore the 13th/14th arguments, not the 4th/8th.
        val m = Mat4.cssMatrix3d(listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 20f, 10f, 0f, 1f))
        assertEquals(20f, m.at(0, 3), 0f)                             // row x, translation column
        assertEquals(10f, m.at(1, 3), 0f)                             // row y, translation column
        val (x, y) = m.project(0f, 0f)
        assertEquals(20f, x, 0f)
        assertEquals(10f, y, 0f)
    }

    @Test
    fun `planeHomography carries the perspective row Skia needs`() {
        // The 3x3 the canvas concatenates: rows x, y, w of the 4x4 restricted
        // to the plane. For perspective(500) translateZ(250) the w row's
        // constant term is m33 = 1 − 250/500 = 0.5 — the entry
        // android.graphics.Matrix reads as MPERSP_2.
        val h = (Mat4.perspective(500f) * Mat4.translate(0f, 0f, 250f)).planeHomography()
        assertEquals(9, h.size)
        assertEquals(0.5f, h[8], 1e-6f)
        assertEquals(0f, h[6], 0f)                                    // no x-dependent divisor
        assertEquals(0f, h[7], 0f)                                    // no y-dependent divisor
    }

    @Test
    fun `a diagonal rotate3d axis flattens to the symmetric squeeze, not a rotateY`() {
        // rotate3d(1,1,0,45deg), §16 quaternion form over n = (1/√2, 1/√2, 0):
        // sq = sin²(22.5°) = 0.14645, sc = sin·cos(22.5°) = 0.35355 →
        // m00 = 1 − 2·(ny²)·sq = 0.85355, m01 = 2·nx·ny·sq = 0.14645,
        // m10 = 0.14645, m11 = 0.85355 — a squeeze along the (1,−1)
        // diagonal. The largest-axis heuristic drew rotateY(45deg) instead,
        // i.e. a = cos 45° = 0.7071 with b = 0 (pairs-06 035/040, 0.71–0.81).
        val a = Mat4.rotate3d(1f, 1f, 0f, 45f).toAffine2D()
        assertEquals(0.85355f, a.a, 1e-4f)
        assertEquals(0.14645f, a.b, 1e-4f)
        assertEquals(0.14645f, a.c, 1e-4f)
        assertEquals(0.85355f, a.d, 1e-4f)
    }

    @Test
    fun `rotateY flattens to the cos scale the orthographic route already draws`() {
        // Cross-check against OrthographicFlatten (wave 49): the 4x4 route
        // and the graphicsLayer route must agree on the same input, or the
        // routing gate would change pixels merely by changing owner.
        val (c, _) = cs(60.0)
        val a = Mat4.rotate3d(0f, 1f, 0f, 60f).toAffine2D()
        assertEquals(c, a.a, 1e-6f)                                   // scaleX = cos 60° = 0.5
        assertEquals(1f, a.d, 1e-6f)                                  // Y untouched
        assertEquals(OrthographicFlatten.of(0f, 60f).scaleX, a.a, 1e-6f)
    }

    @Test
    fun `a zero rotate3d axis is the identity, not a guessed axis`() {
        // css-transforms-2 §12.2: "a direction vector that cannot be
        // normalized, such as [0, 0, 0], will cause the rotation to not be
        // applied" — the old heuristic sent it to rotateZ(θ).
        val (x, y) = Mat4.rotate3d(0f, 0f, 0f, 90f).project(1f, 0f)
        assertEquals(1f, x, 0f)
        assertEquals(0f, y, 0f)
    }
}
