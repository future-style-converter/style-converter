package com.styleconverter.runtime.transforms

// Pure math — deliberately NO Compose UI / android imports, so the whole file
// is exercisable from the plain-JVM unit suite (the folder rule stated in
// TransformListComposer.kt). TransformMatrixPathApplier converts the result
// to a Compose Matrix at the one canvas call site.
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A 4x4 homogeneous transform in the convention css-transforms-2 §16
 * ("Mathematical Description of Transform Functions") writes its matrices
 * in: column vectors, so a point maps as p' = M·p and css-transforms-2 §6's
 * "multiply by each of the transform functions in transform from left to
 * right" is a POST-multiply — the rightmost function maps the point first,
 * exactly like [TransformListComposer.multiply] for the 2D case.
 *
 * Why this exists (retro R1, findings A11#6 / A11#11 / A10#1): every
 * legacy Compose route accumulates transform KINDS into scalars and hands
 * them to graphicsLayer, whose camera model cannot express a CSS
 * perspective projection at all (the wave-49 captures render
 * `perspective(500px) rotateY(30deg)` as a flat rectangle while web/iOS
 * draw the keystoned trapezoid), silently drops `matrix3d()`, maps a
 * diagonal `rotate3d()` axis to its largest component, and throws away the
 * sin·sin shear of a two-axis rotation. A flat element's content lives on
 * the z = 0 plane, so the FULL 4x4 collapses exactly to a 3x3 homography
 * of the plane ([planeHomography]) — which Skia rasterises natively via
 * Canvas.concat, perspective row included. No decomposition, no camera.
 *
 * Storage is row-major: entry (r, c) lives at `v[r * 4 + c]`.
 */
class Mat4 private constructor(private val v: FloatArray) {

    /** Entry at row [r], column [c] of the 4x4. */
    fun at(r: Int, c: Int): Float = v[r * 4 + c]

    /** Matrix product this·n — n maps the point FIRST (§6 step 7 order). */
    operator fun times(n: Mat4): Mat4 {
        val out = FloatArray(16)                          // fresh product
        for (r in 0 until 4) for (c in 0 until 4) {
            var s = 0f                                    // dot of row r with column c
            for (k in 0 until 4) s += v[r * 4 + k] * n.v[k * 4 + c]
            out[r * 4 + c] = s
        }
        return Mat4(out)
    }

    /**
     * True when the plane z = 0 maps affinely: the homogeneous row acting
     * on (x, y, 0, 1) is (m30·x + m31·y + m33) and must be the constant 1.
     * m32 is irrelevant because the content has no z extent. With no
     * perspective in the product this is always true (css-transforms-2
     * §4.1: the 4x4 is then flattened by dropping the z row and column).
     */
    fun isAffineOnPlane(eps: Float = 1e-6f): Boolean =
        abs(v[12]) < eps && abs(v[13]) < eps && abs(v[15] - 1f) < eps

    /**
     * The exact 2D affine of the flattened plane — css-transforms-2 §4.1's
     * "drop the z row and column" applied to a matrix that passed
     * [isAffineOnPlane]. Written in the css-transforms-1 §7.1 matrix(a,b,c,
     * d,tx,ty) column convention TransformListComposer already uses.
     */
    fun toAffine2D(): TransformListComposer.Affine2D = TransformListComposer.Affine2D(
        a = v[0], b = v[4],                               // first column  (image of (1,0))
        c = v[1], d = v[5],                               // second column (image of (0,1))
        tx = v[3], ty = v[7],                             // translation   (image of (0,0))
    )

    /**
     * The 3x3 homography the 4x4 induces on the z = 0 plane, row-major in
     * android.graphics.Matrix's slot order (MSCALE_X, MSKEW_X, MTRANS_X,
     * MSKEW_Y, MSCALE_Y, MTRANS_Y, MPERSP_0, MPERSP_1, MPERSP_2): rows x, y
     * and w of the 4x4, columns x, y and the translation. Exact for flat
     * content — the z column only ever multiplies the content's z, which
     * is 0.
     */
    fun planeHomography(): FloatArray = floatArrayOf(
        v[0], v[1], v[3],                                 // X = m00·x + m01·y + m03
        v[4], v[5], v[7],                                 // Y = m10·x + m11·y + m13
        v[12], v[13], v[15],                              // W = m30·x + m31·y + m33
    )

    /** Project a plane point (x, y, 0, 1) and divide by w — for tests and pins. */
    fun project(x: Float, y: Float): Pair<Float, Float> {
        val w = v[12] * x + v[13] * y + v[15]             // homogeneous divisor
        return Pair(
            (v[0] * x + v[1] * y + v[3]) / w,             // X / W
            (v[4] * x + v[5] * y + v[7]) / w,             // Y / W
        )
    }

    companion object {
        /** The identity — css-transforms-2 §6 step 1 and `transform: none`. */
        val IDENTITY: Mat4 = Mat4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))

        /** translate3d(x, y, z) — §16: translation in the last column. */
        fun translate(x: Float, y: Float, z: Float): Mat4 =
            Mat4(floatArrayOf(1f, 0f, 0f, x, 0f, 1f, 0f, y, 0f, 0f, 1f, z, 0f, 0f, 0f, 1f))

        /** scale3d(sx, sy, sz) — §16: the diagonal. */
        fun scale(sx: Float, sy: Float, sz: Float): Mat4 =
            Mat4(floatArrayOf(sx, 0f, 0f, 0f, 0f, sy, 0f, 0f, 0f, 0f, sz, 0f, 0f, 0f, 0f, 1f))

        /**
         * rotate3d(x, y, z, α) — §16's quaternion-form matrix over the
         * NORMALISED axis, with sq = sin²(α/2) and sc = sin(α/2)·cos(α/2).
         * The z-axis case reduces to [[cos, −sin],[sin, cos]] — the same
         * sign convention as [TransformListComposer.rotation], i.e. CSS's
         * clockwise-positive angles in a y-down frame. A direction vector
         * that cannot be normalised (§12.2: "such as [0,0,0]") makes the
         * rotation NOT apply — identity, not a guess at an axis.
         */
        fun rotate3d(x: Float, y: Float, z: Float, deg: Float): Mat4 {
            val len = sqrt(x * x + y * y + z * z)         // axis length before normalising
            if (len < 1e-12f) return IDENTITY             // §12.2 zero vector → not applied
            val nx = x / len; val ny = y / len; val nz = z / len   // unit axis
            val half = Math.toRadians(deg.toDouble()) / 2.0        // α/2 for the quaternion form
            val sq = (sin(half) * sin(half)).toFloat()    // sin²(α/2)
            val sc = (sin(half) * cos(half)).toFloat()    // sin(α/2)·cos(α/2)
            return Mat4(floatArrayOf(
                1f - 2f * (ny * ny + nz * nz) * sq, 2f * (nx * ny * sq - nz * sc), 2f * (nx * nz * sq + ny * sc), 0f,
                2f * (nx * ny * sq + nz * sc), 1f - 2f * (nx * nx + nz * nz) * sq, 2f * (ny * nz * sq - nx * sc), 0f,
                2f * (nx * nz * sq - ny * sc), 2f * (ny * nz * sq + nx * sc), 1f - 2f * (nx * nx + ny * ny) * sq, 0f,
                0f, 0f, 0f, 1f,
            ))
        }

        /** skew(αx, αy) — §16: tan(αx) shears x by y (row 0), tan(αy) shears y by x (row 1). */
        fun skew(axDeg: Float, ayDeg: Float): Mat4 = Mat4(floatArrayOf(
            1f, tan(Math.toRadians(axDeg.toDouble())).toFloat(), 0f, 0f,
            tan(Math.toRadians(ayDeg.toDouble())).toFloat(), 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        ))

        /**
         * perspective(d) — §16: identity except m32 = −1/d, so a point at
         * depth z gets w = 1 − z/d and projects with scale d/(d − z): toward
         * the viewer (z > 0) is larger. The caller clamps d per §8/§12.2
         * (values below 1px are treated as 1px) — see
         * [TransformMatrixComposer.clampPerspectivePx].
         */
        fun perspective(dPx: Float): Mat4 =
            Mat4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, -1f / dPx, 1f))

        /** matrix(a, b, c, d, e, f) — css-transforms-1 §7.1: [a c 0 e; b d 0 f; 0 0 1 0; 0 0 0 1]. */
        fun cssMatrix(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): Mat4 =
            Mat4(floatArrayOf(a, c, 0f, e, b, d, 0f, f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))

        /**
         * matrix3d(a1 b1 c1 d1 a2 … d4) — css-transforms-2 §12.2: "16 values
         * in column-major order", so argument k = col·4 + row lands at our
         * row-major slot row·4 + col. The converter's serializer emits the
         * keys a1,b1,c1,d1,a2,… in exactly this order (TransformExtractor
         * reads them into a 16-list, first column first).
         */
        fun cssMatrix3d(columnMajor: List<Float>): Mat4 {
            require(columnMajor.size == 16) { "matrix3d needs 16 values" }
            val out = FloatArray(16)                      // row-major destination
            for (col in 0 until 4) for (row in 0 until 4) out[row * 4 + col] = columnMajor[col * 4 + row]
            return Mat4(out)
        }
    }
}
