package com.styleconverter.runtime.transforms

// Pure classification — no Compose imports, JVM-pinnable.
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Classifies a css-transforms-2 §12.2 `rotate3d(x, y, z, α)` axis (also the
 * `rotate: x y z <angle>` longhand of §5) into the representation the
 * renderer draws exactly — retro R1, findings A11#6 / A11#11.
 *
 * Before this file the extractor picked the LARGEST component: `rotate3d
 * (1, 1, 0, 45deg)` became `rotateY(45deg)` (a cos 45° squeeze of the x
 * axis) where the true orthographic image is the symmetric matrix
 * [[0.8536, 0.1464], [0.1464, 0.8536]] — a squeeze along the (1,−1)
 * diagonal (pairs-06 035/040 scored 0.71–0.81 on Android). Axis-aligned
 * axes are still returned as the single-axis functions so every wave-48/49
 * route (and its baseline texture) is untouched for the corpus's
 * rotate3d(1,0,0,…)/(0,1,0,…)/(0,0,1,…) carriers; a NEGATIVE unit axis
 * flips the angle (rotate3d(0,0,−1,θ) = rotateZ(−θ)), which the old
 * heuristic got wrong too.
 */
object Rotate3dAxis {

    /**
     * @return the exact function for this axis/angle, or null when §12.2
     *   says the rotation is NOT applied ("a direction vector that cannot be
     *   normalized, such as [0,0,0]").
     */
    fun classify(x: Float, y: Float, z: Float, degrees: Float): TransformFunction? {
        val len = sqrt(x * x + y * y + z * z)              // can the axis be normalised?
        if (len < 1e-12f) return null                     // §12.2: not applied → identity
        val nx = x / len; val ny = y / len; val nz = z / len   // unit axis
        // Exactly one non-zero component → the single-axis function, angle
        // signed by the axis direction (rotate3d(−1,0,0,θ) = rotateX(−θ)).
        val eps = 1e-6f
        if (abs(ny) < eps && abs(nz) < eps) return TransformFunction.RotateX(degrees * sign(nx))
        if (abs(nx) < eps && abs(nz) < eps) return TransformFunction.RotateY(degrees * sign(ny))
        if (abs(nx) < eps && abs(ny) < eps) return TransformFunction.RotateZ(degrees * sign(nz))
        // Genuinely diagonal — keep the axis; Mat4.rotate3d draws it exactly.
        return TransformFunction.Rotate3d(nx, ny, nz, degrees)
    }

    /** ±1 for a non-zero component (zero never reaches here on the chosen axis). */
    private fun sign(v: Float): Float = if (v < 0f) -1f else 1f
}
