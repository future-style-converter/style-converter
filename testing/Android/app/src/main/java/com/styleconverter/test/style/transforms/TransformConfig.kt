package com.styleconverter.test.style.transforms

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for transform-related styling properties.
 *
 * This config aggregates all CSS transform properties into a single structure
 * that can be applied to a Modifier using graphicsLayer.
 *
 * ## Supported Properties
 * - Transform: CSS transform property with multiple functions (translate, rotate, scale, skew, etc.)
 * - TransformOrigin: Pivot point for transformations (default: center)
 * - Rotate: Standalone rotation property (degrees)
 * - Scale: Standalone scale property
 * - Translate: Standalone translation property
 *
 * ## Usage
 * ```kotlin
 * val config = TransformExtractor.extractTransformConfig(properties)
 * val modifier = TransformApplier.applyTransforms(Modifier, config)
 * ```
 *
 * ## Compose Mapping
 * - CSS `transform` -> `Modifier.graphicsLayer { ... }`
 * - CSS `transform-origin` -> `TransformOrigin(x, y)` in graphicsLayer
 * - CSS `rotate` -> `Modifier.rotate()` or graphicsLayer rotationZ
 * - CSS `scale` -> `Modifier.scale()` or graphicsLayer scaleX/scaleY
 * - CSS `translate` -> `Modifier.offset()` or graphicsLayer translationX/Y
 */
data class TransformConfig(
    /** List of transform functions from CSS transform property */
    val functions: List<TransformFunction> = emptyList(),
    /** Transform origin X (0-1, where 0.5 is center) */
    val originX: Float = 0.5f,
    /** Transform origin Y (0-1, where 0.5 is center) */
    val originY: Float = 0.5f,
    /**
     * Absolute (Dp) override for the transform origin. CSS allows lengths
     * on `transform-origin` (e.g. `transform-origin: 10px 10px`); they need
     * the element's own size to convert to a 0..1 fraction, which we don't
     * know at extract time. When non-null, the applier resolves these in
     * the graphicsLayer block where `size` is in scope and feeds the result
     * into `transformOrigin` instead of the fractional `originX/Y`.
     */
    val originXDp: Dp? = null,
    val originYDp: Dp? = null,
    /** Standalone rotation in degrees (CSS rotate property) */
    val rotate: Float? = null,
    /** Standalone rotation around X axis (3D) */
    val rotateX: Float? = null,
    /** Standalone rotation around Y axis (3D) */
    val rotateY: Float? = null,
    /** Uniform scale factor (CSS scale property) */
    val scale: Float? = null,
    /** Horizontal scale factor */
    val scaleX: Float? = null,
    /** Vertical scale factor */
    val scaleY: Float? = null,
    /** Horizontal translation */
    val translateX: Dp? = null,
    /** Vertical translation */
    val translateY: Dp? = null,
    /** Z-axis translation (for 3D depth simulation) */
    val translateZ: Dp? = null,
    /**
     * Horizontal translation as a fraction (0.0–1.0) of the element's own
     * width. CSS allows percentages on the `translate` longhand and the
     * `translate(...)` function; they reference the element's dimensions
     * and must therefore be resolved at draw time. Stored alongside the
     * absolute Dp field so callers can additively combine the two.
     */
    val translateXFraction: Float? = null,
    /** Vertical translation as a fraction (0.0–1.0) of the element's own height. */
    val translateYFraction: Float? = null,
    /** Z-axis scale (for 3D depth simulation) */
    val scaleZ: Float? = null,
    /** Skew X angle in degrees */
    val skewX: Float? = null,
    /** Skew Y angle in degrees */
    val skewY: Float? = null,
    /** Perspective distance for 3D transforms */
    val perspective: Dp? = null
) {
    /**
     * Returns true if any transform is applied.
     */
    val hasTransform: Boolean
        get() = functions.isNotEmpty() ||
                rotate != null ||
                rotateX != null ||
                rotateY != null ||
                scale != null ||
                scaleX != null ||
                scaleY != null ||
                scaleZ != null ||
                translateX != null ||
                translateY != null ||
                translateZ != null ||
                translateXFraction != null ||
                translateYFraction != null ||
                skewX != null ||
                skewY != null ||
                perspective != null

    /**
     * Returns true if 3D transforms are present (translateZ, scaleZ, rotateX, rotateY).
     */
    val has3DTransform: Boolean
        get() = translateZ != null ||
                scaleZ != null ||
                rotateX != null ||
                rotateY != null ||
                perspective != null ||
                functions.any {
                    it is TransformFunction.TranslateZ ||
                    it is TransformFunction.ScaleZ ||
                    it is TransformFunction.RotateX ||
                    it is TransformFunction.RotateY ||
                    it is TransformFunction.Perspective ||
                    (it is TransformFunction.Translate && it.z.value != 0f) ||
                    (it is TransformFunction.Scale && it.z != 1f)
                }

    /**
     * Returns true if the transform origin is not center (default).
     */
    val hasCustomOrigin: Boolean
        get() = originX != 0.5f || originY != 0.5f ||
                originXDp != null || originYDp != null

    /**
     * Returns true if translation is set.
     */
    val hasTranslate: Boolean
        get() = translateX != null || translateY != null ||
                translateXFraction != null || translateYFraction != null

    /**
     * Returns true if scaling is set.
     */
    val hasScale: Boolean
        get() = scale != null || scaleX != null || scaleY != null

    /**
     * Returns true if skew is set.
     */
    val hasSkew: Boolean
        get() = skewX != null || skewY != null ||
                functions.any { it is TransformFunction.Skew || it is TransformFunction.SkewX || it is TransformFunction.SkewY }

    /**
     * Returns true if this is a simple transform that can use basic modifiers.
     * Complex transforms require graphicsLayer.
     */
    val isSimpleTransform: Boolean
        get() = functions.isEmpty() &&
                !hasSkew &&
                !has3DTransform &&
                // Fractional translate needs the element's own size, which
                // Modifier.offset can't read — force the graphicsLayer path.
                translateXFraction == null &&
                translateYFraction == null
}

/**
 * Sealed interface representing CSS transform functions.
 *
 * ## Supported Functions
 * - translate, translateX, translateY, translateZ: Move element
 * - rotate, rotateX, rotateY, rotateZ: Rotate element
 * - scale, scaleX, scaleY, scaleZ: Scale element
 * - skew, skewX, skewY: Skew element (limited support in Compose)
 * - perspective: Set 3D perspective
 * - matrix, matrix3d: Direct matrix transforms (limited support)
 * - none: No transform
 *
 * ## Compose Limitations
 * - Skew transforms are not directly supported in graphicsLayer
 * - matrix/matrix3d would need decomposition for proper support
 * - translateZ has limited effect without perspective
 */
sealed interface TransformFunction {

    /**
     * 2D/3D translation.
     *
     * CSS: `translate(x, y)` or `translate3d(x, y, z)`
     * Compose: `graphicsLayer { translationX = ...; translationY = ... }`
     */
    data class Translate(val x: Dp, val y: Dp, val z: Dp = 0.dp) : TransformFunction

    /**
     * Horizontal translation.
     *
     * CSS: `translateX(x)`
     * Compose: `graphicsLayer { translationX = ... }`
     */
    data class TranslateX(val x: Dp) : TransformFunction

    /**
     * Vertical translation.
     *
     * CSS: `translateY(y)`
     * Compose: `graphicsLayer { translationY = ... }`
     */
    data class TranslateY(val y: Dp) : TransformFunction

    /**
     * Z-axis translation (3D).
     *
     * CSS: `translateZ(z)`
     * Compose: Limited support (no direct Z translation in 2D space)
     */
    data class TranslateZ(val z: Dp) : TransformFunction

    /**
     * 2D rotation around Z axis.
     *
     * CSS: `rotate(angle)`
     * Compose: `graphicsLayer { rotationZ = ... }`
     */
    data class Rotate(val degrees: Float) : TransformFunction

    /**
     * 3D rotation around X axis.
     *
     * CSS: `rotateX(angle)`
     * Compose: `graphicsLayer { rotationX = ... }`
     */
    data class RotateX(val degrees: Float) : TransformFunction

    /**
     * 3D rotation around Y axis.
     *
     * CSS: `rotateY(angle)`
     * Compose: `graphicsLayer { rotationY = ... }`
     */
    data class RotateY(val degrees: Float) : TransformFunction

    /**
     * 3D rotation around Z axis (explicit).
     *
     * CSS: `rotateZ(angle)`
     * Compose: `graphicsLayer { rotationZ = ... }`
     */
    data class RotateZ(val degrees: Float) : TransformFunction

    /**
     * 2D/3D scaling.
     *
     * CSS: `scale(x, y)` or `scale3d(x, y, z)`
     * Compose: `graphicsLayer { scaleX = ...; scaleY = ... }`
     */
    data class Scale(val x: Float, val y: Float, val z: Float = 1f) : TransformFunction

    /**
     * Horizontal scaling.
     *
     * CSS: `scaleX(x)`
     * Compose: `graphicsLayer { scaleX = ... }`
     */
    data class ScaleX(val x: Float) : TransformFunction

    /**
     * Vertical scaling.
     *
     * CSS: `scaleY(y)`
     * Compose: `graphicsLayer { scaleY = ... }`
     */
    data class ScaleY(val y: Float) : TransformFunction

    /**
     * Z-axis scaling (3D).
     *
     * CSS: `scaleZ(z)`
     * Compose: Limited support (no direct Z scale effect in 2D rendering)
     */
    data class ScaleZ(val z: Float) : TransformFunction

    /**
     * 2D skew on both axes.
     *
     * CSS: `skew(x-angle, y-angle)`
     * Compose: Not directly supported; approximation via matrix possible
     */
    data class Skew(val xDegrees: Float, val yDegrees: Float) : TransformFunction

    /**
     * Horizontal skew.
     *
     * CSS: `skewX(angle)`
     * Compose: Not directly supported
     */
    data class SkewX(val degrees: Float) : TransformFunction

    /**
     * Vertical skew.
     *
     * CSS: `skewY(angle)`
     * Compose: Not directly supported
     */
    data class SkewY(val degrees: Float) : TransformFunction

    /**
     * 3D perspective.
     *
     * CSS: `perspective(distance)`
     * Compose: `graphicsLayer { cameraDistance = ... }`
     */
    data class Perspective(val distance: Dp) : TransformFunction

    /**
     * 2D affine transformation matrix.
     *
     * CSS: `matrix(a, b, c, d, tx, ty)`
     * Compose: Would need decomposition into scale, rotate, translate
     */
    data class Matrix(val values: List<Float>) : TransformFunction

    /**
     * 3D transformation matrix.
     *
     * CSS: `matrix3d(16 values)`
     * Compose: Would need decomposition; limited support
     */
    data class Matrix3d(val values: List<Float>) : TransformFunction

    /**
     * No transformation.
     *
     * CSS: `transform: none`
     */
    data object None : TransformFunction
}
