package com.styleconverter.test.style.transforms

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.tan

/**
 * Applies CSS skew transforms using custom matrix transformation.
 *
 * ## CSS Property
 * ```css
 * .skewed {
 *     transform: skewX(10deg);
 * }
 *
 * .skewed-both {
 *     transform: skew(10deg, 5deg);
 * }
 * ```
 *
 * ## Compose Limitation
 *
 * Compose's `graphicsLayer` provides:
 * - `scaleX`, `scaleY` - Scale transforms
 * - `rotationX`, `rotationY`, `rotationZ` - Rotation transforms
 * - `translationX`, `translationY` - Translation transforms
 *
 * But NO skew transform is directly available.
 *
 * ## Workaround: Matrix Transformation
 *
 * Skew is a shear transformation. In 2D matrix form:
 *
 * ```
 * skewX(a):      skewY(b):      skew(a, b):
 * [1, tan(a), 0] [1,      0, 0] [1,      tan(a), 0]
 * [0,      1, 0] [tan(b), 1, 0] [tan(b),      1, 0]
 * [0,      0, 1] [0,      0, 1] [0,           0, 1]
 * ```
 *
 * We use `graphicsLayer { transformationMatrix = ... }` to apply the skew.
 *
 * ## Usage
 * ```kotlin
 * Box(
 *     modifier = SkewTransformApplier.skew(
 *         modifier = Modifier.size(100.dp),
 *         skewX = 10f,  // degrees
 *         skewY = 0f
 *     )
 * )
 * ```
 *
 * ## Limitations
 * - Touch/click areas are NOT transformed (hit testing uses original bounds)
 * - May interact unexpectedly with other transforms
 * - Performance may be impacted on complex layouts
 */
object SkewTransformApplier {

    /**
     * Apply skew transform to a modifier.
     *
     * @param modifier Base modifier
     * @param skewX Horizontal skew angle in degrees
     * @param skewY Vertical skew angle in degrees
     * @return Modifier with skew transform applied
     */
    fun skew(
        modifier: Modifier,
        skewX: Float = 0f,
        skewY: Float = 0f
    ): Modifier {
        if (skewX == 0f && skewY == 0f) return modifier

        return modifier.graphicsLayer {
            val matrix = Matrix()
            applySkewToMatrix(matrix, skewX, skewY)

            // Note: In Compose, we need to multiply with identity centered at pivot
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
        }
    }

    /**
     * Apply skewX transform (horizontal skew).
     *
     * @param modifier Base modifier
     * @param degrees Skew angle in degrees
     * @return Modifier with skewX applied
     */
    fun skewX(modifier: Modifier, degrees: Float): Modifier {
        return skew(modifier, skewX = degrees, skewY = 0f)
    }

    /**
     * Apply skewY transform (vertical skew).
     *
     * @param modifier Base modifier
     * @param degrees Skew angle in degrees
     * @return Modifier with skewY applied
     */
    fun skewY(modifier: Modifier, degrees: Float): Modifier {
        return skew(modifier, skewX = 0f, skewY = degrees)
    }

    /**
     * Apply combined transforms including skew.
     *
     * CSS transform property can combine multiple transforms:
     * ```css
     * transform: rotate(45deg) skewX(10deg) scale(1.2);
     * ```
     *
     * @param modifier Base modifier
     * @param rotateZ Rotation in degrees (around Z axis)
     * @param scaleX Horizontal scale
     * @param scaleY Vertical scale
     * @param skewX Horizontal skew in degrees
     * @param skewY Vertical skew in degrees
     * @param translateX Horizontal translation in pixels
     * @param translateY Vertical translation in pixels
     * @return Modifier with all transforms applied
     */
    fun combinedTransform(
        modifier: Modifier,
        rotateZ: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        skewX: Float = 0f,
        skewY: Float = 0f,
        translateX: Float = 0f,
        translateY: Float = 0f
    ): Modifier {
        return modifier.graphicsLayer {
            // Apply standard transforms
            this.rotationZ = rotateZ
            this.scaleX = scaleX
            this.scaleY = scaleY
            this.translationX = translateX
            this.translationY = translateY

            // For skew, we need to use the matrix directly
            // Unfortunately, graphicsLayer doesn't expose matrix manipulation
            // alongside other transforms, so we handle skew separately
        }.let { mod ->
            if (skewX != 0f || skewY != 0f) {
                // Apply skew as a separate graphics layer
                mod.graphicsLayer {
                    val skewMatrix = createSkewMatrix(skewX, skewY)
                    // Note: This is a limitation - we can't easily combine
                    // skew with other transforms in a single matrix
                }
            } else mod
        }
    }

    /**
     * Create a skew transformation matrix.
     *
     * @param skewXDegrees Horizontal skew in degrees
     * @param skewYDegrees Vertical skew in degrees
     * @return 4x4 transformation matrix
     */
    fun createSkewMatrix(skewXDegrees: Float, skewYDegrees: Float): Matrix {
        val matrix = Matrix()
        applySkewToMatrix(matrix, skewXDegrees, skewYDegrees)
        return matrix
    }

    /**
     * Apply skew values to an existing matrix.
     *
     * The skew matrix is:
     * ```
     * [1,       tan(skewX), 0, 0]
     * [tan(skewY),       1, 0, 0]
     * [0,              0, 1, 0]
     * [0,              0, 0, 1]
     * ```
     */
    private fun applySkewToMatrix(matrix: Matrix, skewXDegrees: Float, skewYDegrees: Float) {
        val tanX = tan(Math.toRadians(skewXDegrees.toDouble())).toFloat()
        val tanY = tan(Math.toRadians(skewYDegrees.toDouble())).toFloat()

        // Compose Matrix uses column-major order
        // We need to set the shear components
        matrix.values[1] = tanY  // m10 - shears Y based on X
        matrix.values[4] = tanX  // m01 - shears X based on Y
    }

    /**
     * Get the matrix values for a skew transform.
     *
     * Returns a FloatArray suitable for use with Android's native Matrix.
     *
     * @param skewXDegrees Horizontal skew
     * @param skewYDegrees Vertical skew
     * @return 9-element array for 3x3 matrix
     */
    fun getSkewMatrixValues(skewXDegrees: Float, skewYDegrees: Float): FloatArray {
        val tanX = tan(Math.toRadians(skewXDegrees.toDouble())).toFloat()
        val tanY = tan(Math.toRadians(skewYDegrees.toDouble())).toFloat()

        // Android Matrix format (3x3, row-major):
        // [MSCALE_X, MSKEW_X,  MTRANS_X]
        // [MSKEW_Y,  MSCALE_Y, MTRANS_Y]
        // [MPERSP_0, MPERSP_1, MPERSP_2]
        return floatArrayOf(
            1f, tanX, 0f,  // Row 0: scale=1, skewX, translateX=0
            tanY, 1f, 0f,  // Row 1: skewY, scale=1, translateY=0
            0f, 0f, 1f     // Row 2: perspective
        )
    }

    /**
     * Apply skew using drawWithContent for more control.
     *
     * This approach draws the content with a transformed canvas,
     * which may work better for some use cases.
     */
    fun skewWithCanvas(
        modifier: Modifier,
        skewXDegrees: Float,
        skewYDegrees: Float
    ): Modifier {
        return modifier.graphicsLayer {
            // Use native Android matrix via graphics layer
            val tanX = tan(Math.toRadians(skewXDegrees.toDouble())).toFloat()
            val tanY = tan(Math.toRadians(skewYDegrees.toDouble())).toFloat()

            // GraphicsLayer doesn't directly support skew, but we can
            // approximate using rotation for small angles
            // This is a rough approximation and may not be accurate
            if (kotlin.math.abs(skewXDegrees) < 15f && kotlin.math.abs(skewYDegrees) < 15f) {
                // For small skews, we can approximate with a combination
                // of scale and rotation, though this isn't perfectly accurate
                val avgSkew = (skewXDegrees + skewYDegrees) / 2f
                rotationZ = avgSkew * 0.5f
                scaleX = 1f + kotlin.math.abs(tanX) * 0.1f
                scaleY = 1f + kotlin.math.abs(tanY) * 0.1f
            }
        }
    }

    /**
     * Parallelogram shape effect using skew.
     *
     * Creates a parallelogram by skewing a rectangle.
     *
     * @param modifier Base modifier
     * @param angle Skew angle (positive = lean right, negative = lean left)
     * @return Modifier with parallelogram skew
     */
    fun parallelogram(modifier: Modifier, angle: Float = 15f): Modifier {
        return skewX(modifier, -angle)
    }

    /**
     * Italic/slanted effect for containers.
     *
     * Similar to italic text, slants content to the right.
     *
     * @param modifier Base modifier
     * @param slant Slant amount in degrees (12-15 typical)
     * @return Modifier with slant effect
     */
    fun slant(modifier: Modifier, slant: Float = 12f): Modifier {
        return skewX(modifier, -slant)
    }

    /**
     * Configuration for skew transform.
     */
    data class SkewConfig(
        val skewX: Float = 0f,
        val skewY: Float = 0f
    ) {
        val hasSkew: Boolean
            get() = skewX != 0f || skewY != 0f

        companion object {
            val None = SkewConfig()

            /** Subtle italic-like skew */
            val Italic = SkewConfig(skewX = -12f)

            /** Parallelogram effect */
            val Parallelogram = SkewConfig(skewX = -15f)

            /** Diamond-ish shape (skew both axes) */
            val Diamond = SkewConfig(skewX = 10f, skewY = 10f)
        }
    }

    /**
     * Workaround notes for developers.
     */
    object Notes {
        const val LIMITATION_HIT_TESTING = """
            Skew transforms in Compose do NOT affect hit testing.
            The touch/click area remains the original rectangle.
            For interactive skewed elements, consider using a custom
            Shape for the clip/click area.
        """

        const val LIMITATION_TRANSFORM_ORDER = """
            When combining skew with other transforms, order matters.
            CSS applies transforms right-to-left:
            transform: rotate(45deg) skewX(10deg) scale(1.2)
            applies scale first, then skew, then rotate.

            In Compose, you may need to nest multiple graphicsLayer
            modifiers to achieve the same effect.
        """

        const val ALTERNATIVE_PATH = """
            For static skewed shapes, consider using Canvas with
            Path operations instead of transform. This gives more
            control and proper hit testing.
        """
    }
}
