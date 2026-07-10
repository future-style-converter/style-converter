package com.styleconverter.runtime.transforms

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Applies transform configuration to Compose Modifiers.
 *
 * ## Simple vs Complex Transforms
 *
 * Simple transforms (translate, rotate, scale without custom origin or skew)
 * can use basic Modifiers:
 * - Modifier.offset() for translate
 * - Modifier.rotate() for rotation
 * - Modifier.scale() for scaling
 *
 * Complex transforms (skew, custom origin, 3D, combined) use graphicsLayer:
 * - graphicsLayer { translationX/Y, rotationZ, scaleX/Y, transformOrigin }
 *
 * ## Skew Transform Implementation
 * Skew is implemented using drawWithContent with Canvas.skew() since
 * graphicsLayer doesn't support skew directly. The skew values are converted
 * from degrees to radians for the Canvas API.
 *
 * ## Matrix Transform Implementation
 * CSS matrix(a, b, c, d, tx, ty) is decomposed into scale, rotation, skew,
 * and translation components and applied separately.
 *
 * ## Order of Operations
 * CSS transforms are applied in order (left to right in the transform string).
 * Compose graphicsLayer applies in order: scale -> rotate -> translate.
 * For exact CSS matching, complex transforms use custom Canvas rendering.
 *
 * ## Limitations
 * - 3D transforms (rotateX/Y, translateZ) have limited Compose support
 * - Perspective uses cameraDistance approximation
 */
object TransformApplier {

    /**
     * Apply transform configuration to a Modifier.
     *
     * Chooses between simple modifiers and graphicsLayer based on
     * the complexity of the transform.
     *
     * @param modifier The base modifier to extend
     * @param config The transform configuration to apply
     * @return Modified Modifier with transforms applied
     */
    fun applyTransforms(modifier: Modifier, config: TransformConfig): Modifier {
        if (!config.hasTransform) return modifier

        // Check if we have skew transforms - these need special handling
        if (config.hasSkew) {
            return applyTransformsWithSkew(modifier, config)
        }

        // Check if we have matrix transforms - these need decomposition
        val hasMatrix = config.functions.any { it is TransformFunction.Matrix || it is TransformFunction.Matrix3d }
        if (hasMatrix) {
            return applyTransformsWithMatrix(modifier, config)
        }

        // If we have transform functions, use graphicsLayer for combined transforms
        return if (config.functions.isNotEmpty()) {
            applyTransformFunctions(modifier, config)
        } else if (!config.isSimpleTransform || config.hasCustomOrigin) {
            // Use graphicsLayer for complex standalone transforms
            applyWithGraphicsLayer(modifier, config)
        } else {
            // Use simple modifiers for basic transforms
            applySimpleTransforms(modifier, config)
        }
    }

    /** Default perspective distance for Z-axis calculations when none specified */
    private const val DEFAULT_PERSPECTIVE = 1000f

    /**
     * Apply transforms that include skew using Canvas transformations.
     * This provides true skew support that graphicsLayer cannot offer.
     */
    private fun applyTransformsWithSkew(modifier: Modifier, config: TransformConfig): Modifier {
        // Collect all transform values
        var translateX = 0f
        var translateY = 0f
        var translateZ = 0f
        var rotation = 0f
        var scaleX = 1f
        var scaleY = 1f
        var scaleZ = 1f
        var skewX = 0f
        var skewY = 0f
        var rotationX = 0f
        var rotationY = 0f
        var cameraDistance = 0f

        // Process transform functions
        config.functions.forEach { fn ->
            when (fn) {
                is TransformFunction.Translate -> {
                    // Dp will be converted in drawWithContent
                    translateX += fn.x.value
                    translateY += fn.y.value
                    translateZ += fn.z.value
                }
                is TransformFunction.TranslateX -> translateX += fn.x.value
                is TransformFunction.TranslateY -> translateY += fn.y.value
                is TransformFunction.TranslateZ -> translateZ += fn.z.value
                is TransformFunction.Rotate -> rotation += fn.degrees
                is TransformFunction.RotateX -> rotationX += fn.degrees
                is TransformFunction.RotateY -> rotationY += fn.degrees
                is TransformFunction.RotateZ -> rotation += fn.degrees
                is TransformFunction.Scale -> {
                    scaleX *= fn.x
                    scaleY *= fn.y
                    scaleZ *= fn.z
                }
                is TransformFunction.ScaleX -> scaleX *= fn.x
                is TransformFunction.ScaleY -> scaleY *= fn.y
                is TransformFunction.ScaleZ -> scaleZ *= fn.z
                is TransformFunction.Skew -> {
                    skewX += fn.xDegrees
                    skewY += fn.yDegrees
                }
                is TransformFunction.SkewX -> skewX += fn.degrees
                is TransformFunction.SkewY -> skewY += fn.degrees
                is TransformFunction.Perspective -> {
                    cameraDistance = fn.distance.value
                }
                is TransformFunction.Matrix -> {
                    // Decompose matrix and add to transforms
                    val decomposed = decomposeMatrix2D(fn.values)
                    translateX += decomposed.translateX
                    translateY += decomposed.translateY
                    rotation += decomposed.rotation
                    scaleX *= decomposed.scaleX
                    scaleY *= decomposed.scaleY
                    skewX += decomposed.skewX
                }
                is TransformFunction.Matrix3d -> { /* Complex - skip for now */ }
                is TransformFunction.None -> {
                    translateX = 0f
                    translateY = 0f
                    translateZ = 0f
                    rotation = 0f
                    scaleX = 1f
                    scaleY = 1f
                    scaleZ = 1f
                    skewX = 0f
                    skewY = 0f
                }
            }
        }

        // Add standalone properties
        config.translateX?.let { translateX += it.value }
        config.translateY?.let { translateY += it.value }
        config.translateZ?.let { translateZ += it.value }
        config.rotate?.let { rotation += it }
        config.rotateX?.let { rotationX += it }
        config.rotateY?.let { rotationY += it }
        config.scaleX?.let { scaleX *= it }
        config.scaleY?.let { scaleY *= it }
        config.scaleZ?.let { scaleZ *= it }
        config.scale?.let { scale ->
            scaleX *= scale
            scaleY *= scale
        }
        config.skewX?.let { skewX += it }
        config.skewY?.let { skewY += it }
        config.perspective?.let { cameraDistance = it.value }

        // Convert skew degrees to radians for tan()
        val skewXRad = Math.toRadians(skewX.toDouble()).toFloat()
        val skewYRad = Math.toRadians(skewY.toDouble()).toFloat()

        val originX = config.originX
        val originY = config.originY

        // Apply transforms using drawWithContent for skew support
        return modifier.drawWithContent {
            val density = this.density
            val pivotX = size.width * originX
            val pivotY = size.height * originY

            // Convert Dp values to pixels
            val txPx = translateX * density
            val tyPx = translateY * density
            val tzPx = translateZ * density

            // Calculate perspective distance (use default if not specified)
            val perspectivePx = if (cameraDistance > 0) cameraDistance * density else DEFAULT_PERSPECTIVE * density

            // Calculate depth-based scale factor from translateZ
            // CSS formula: scale = 1 + (translateZ / perspective)
            // Positive translateZ = element comes toward viewer = appears larger
            // Negative translateZ = element goes away = appears smaller
            val depthScale = if (perspectivePx > 0 && tzPx != 0f) {
                (1f + tzPx / perspectivePx).coerceIn(0.1f, 10f)
            } else {
                1f
            }

            drawContext.canvas.let { canvas ->
                canvas.save()

                // Move to transform origin
                canvas.translate(pivotX, pivotY)

                // Apply perspective effect
                // Note: True perspective would require a perspective projection matrix,
                // but we approximate using scale based on distance from camera
                if (cameraDistance > 0) {
                    // Apply perspective-based scale reduction for distant objects
                    // This simulates objects getting smaller as they move away
                    val perspectiveScale = perspectivePx / (perspectivePx + tzPx)
                    canvas.scale(perspectiveScale.coerceIn(0.1f, 10f), perspectiveScale.coerceIn(0.1f, 10f))
                } else if (tzPx != 0f) {
                    // No explicit perspective, but we have translateZ
                    // Apply basic depth scaling
                    canvas.scale(depthScale, depthScale)
                }

                // Apply 3D rotations if present (approximate with scale)
                // scaleZ affects how the element stretches during 3D rotation
                if (rotationX != 0f) {
                    val rx = Math.toRadians(rotationX.toDouble())
                    val cosRx = cos(rx).toFloat()
                    // scaleZ affects the Y-axis during X rotation
                    val effectiveScaleY = cosRx * scaleZ
                    canvas.scale(1f, abs(effectiveScaleY).coerceAtLeast(0.01f))
                }
                if (rotationY != 0f) {
                    val ry = Math.toRadians(rotationY.toDouble())
                    val cosRy = cos(ry).toFloat()
                    // scaleZ affects the X-axis during Y rotation
                    val effectiveScaleX = cosRy * scaleZ
                    canvas.scale(abs(effectiveScaleX).coerceAtLeast(0.01f), 1f)
                }

                // Apply scale (2D)
                canvas.scale(scaleX, scaleY)

                // Apply rotation
                canvas.rotate(rotation)

                // Apply skew
                if (skewX != 0f || skewY != 0f) {
                    canvas.skew(tan(skewXRad), tan(skewYRad))
                }

                // Move back from transform origin
                canvas.translate(-pivotX, -pivotY)

                // Apply translation
                canvas.translate(txPx, tyPx)

                // Draw content
                this@drawWithContent.drawContent()

                canvas.restore()
            }
        }
    }

    /**
     * Apply transforms that include matrix functions.
     */
    private fun applyTransformsWithMatrix(modifier: Modifier, config: TransformConfig): Modifier {
        // For matrix transforms, we decompose and apply as regular transforms
        // This uses the skew path since decomposed matrices often include skew
        return applyTransformsWithSkew(modifier, config)
    }

    /**
     * Decompose a 2D CSS matrix into component transforms.
     *
     * CSS matrix(a, b, c, d, tx, ty) where:
     * | a  c  tx |
     * | b  d  ty |
     * | 0  0  1  |
     */
    private fun decomposeMatrix2D(values: List<Float>): DecomposedMatrix {
        if (values.size < 6) {
            return DecomposedMatrix()
        }

        val a = values[0]
        val b = values[1]
        val c = values[2]
        val d = values[3]
        val tx = values[4]
        val ty = values[5]

        // Calculate scale
        var scaleX = kotlin.math.sqrt(a * a + b * b)
        var scaleY = kotlin.math.sqrt(c * c + d * d)

        // Check for negative scale (reflection)
        val det = a * d - b * c
        if (det < 0) {
            scaleX = -scaleX
        }

        // Calculate rotation
        val rotation = Math.toDegrees(kotlin.math.atan2(b.toDouble(), a.toDouble())).toFloat()

        // Calculate skew
        val skewX = Math.toDegrees(kotlin.math.atan2((a * c + b * d).toDouble(), (scaleX * scaleY).toDouble())).toFloat()

        return DecomposedMatrix(
            translateX = tx,
            translateY = ty,
            rotation = rotation,
            scaleX = scaleX,
            scaleY = scaleY,
            skewX = skewX
        )
    }

    /**
     * Decomposed matrix components.
     */
    private data class DecomposedMatrix(
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        val rotation: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val skewX: Float = 0f
    )

    /**
     * Apply transform functions using graphicsLayer.
     *
     * This method processes all transform functions from the CSS transform property
     * and combines them into a single graphicsLayer call.
     */
    private fun applyTransformFunctions(modifier: Modifier, config: TransformConfig): Modifier {
        return modifier.graphicsLayer {
            // Set transform origin. Dp overrides resolve against `size`
            // here because CSS lengths on transform-origin reference the
            // element's own dimensions (Transforms 1 §3.2). See parallel
            // comment in applyWithGraphicsLayer above.
            val ox = config.originXDp?.let { dp -> (dp.toPx() / size.width.coerceAtLeast(1f)) } ?: config.originX
            val oy = config.originYDp?.let { dp -> (dp.toPx() / size.height.coerceAtLeast(1f)) } ?: config.originY
            transformOrigin = TransformOrigin(ox, oy)

            // Accumulated values (transforms are cumulative in CSS)
            var totalTranslationX = 0f
            var totalTranslationY = 0f
            var totalTranslationZ = 0f
            var totalRotation = 0f
            var totalRotationX = 0f
            var totalRotationY = 0f
            var totalScaleX = 1f
            var totalScaleY = 1f
            var totalScaleZ = 1f
            var perspectiveDistance = 0f

            // Process each transform function in order
            config.functions.forEach { fn ->
                when (fn) {
                    is TransformFunction.Translate -> {
                        totalTranslationX += fn.x.toPx()
                        totalTranslationY += fn.y.toPx()
                        totalTranslationZ += fn.z.toPx()
                    }
                    is TransformFunction.TranslateX -> {
                        totalTranslationX += fn.x.toPx()
                    }
                    is TransformFunction.TranslateY -> {
                        totalTranslationY += fn.y.toPx()
                    }
                    is TransformFunction.TranslateZ -> {
                        totalTranslationZ += fn.z.toPx()
                    }
                    is TransformFunction.Rotate -> {
                        totalRotation += fn.degrees
                    }
                    is TransformFunction.RotateX -> {
                        totalRotationX += fn.degrees
                    }
                    is TransformFunction.RotateY -> {
                        totalRotationY += fn.degrees
                    }
                    is TransformFunction.RotateZ -> {
                        totalRotation += fn.degrees
                    }
                    is TransformFunction.Scale -> {
                        totalScaleX *= fn.x
                        totalScaleY *= fn.y
                        totalScaleZ *= fn.z
                    }
                    is TransformFunction.ScaleX -> {
                        totalScaleX *= fn.x
                    }
                    is TransformFunction.ScaleY -> {
                        totalScaleY *= fn.y
                    }
                    is TransformFunction.ScaleZ -> {
                        totalScaleZ *= fn.z
                    }
                    is TransformFunction.Perspective -> {
                        perspectiveDistance = fn.distance.toPx()
                    }
                    is TransformFunction.None -> {
                        // No transform - reset to defaults
                        totalTranslationX = 0f
                        totalTranslationY = 0f
                        totalTranslationZ = 0f
                        totalRotation = 0f
                        totalRotationX = 0f
                        totalRotationY = 0f
                        totalScaleX = 1f
                        totalScaleY = 1f
                        totalScaleZ = 1f
                    }
                    else -> { /* Skew/Matrix handled by applyTransformsWithSkew */ }
                }
            }

            // Add standalone properties. css-transforms-2 §5: the individual
            // `translate` / `rotate` / `scale` properties multiply into the
            // final transform BEFORE the `transform` list (translate, then
            // rotate, then scale, then transform). graphicsLayer composes a
            // single translate·rotate·scale-about-pivot matrix, so we fold
            // them into the same accumulators the function list uses — exact
            // for the common uniform-scale/rotate combinations, approximate
            // only when a non-uniform scale meets a rotation. Previously only
            // translateZ/scaleZ were folded, so `scale: 150%` alongside
            // `transform: rotate(15deg)` rendered the rotation UNSCALED
            // (Transforms_C02: 180x48 box vs web/iOS 270x72).
            config.translateX?.let { totalTranslationX += it.toPx() }
            config.translateY?.let { totalTranslationY += it.toPx() }
            config.translateXFraction?.let { totalTranslationX += size.width * it }
            config.translateYFraction?.let { totalTranslationY += size.height * it }
            config.translateZ?.let { totalTranslationZ += it.toPx() }
            config.rotate?.let { totalRotation += it }
            config.rotateX?.let { totalRotationX += it }
            config.rotateY?.let { totalRotationY += it }
            // `scale: <uniform>` and axis-split scale/scaleX/scaleY all
            // multiply per css-transforms-2 §5's matrix accumulation.
            config.scale?.let { totalScaleX *= it; totalScaleY *= it }
            config.scaleX?.let { totalScaleX *= it }
            config.scaleY?.let { totalScaleY *= it }
            config.scaleZ?.let { totalScaleZ *= it }

            // Calculate camera distance from perspective
            // Compose's cameraDistance is in dp relative to screen density
            val effectivePerspective = if (perspectiveDistance > 0) {
                perspectiveDistance / density
            } else if (totalTranslationZ != 0f || totalScaleZ != 1f) {
                // Use default perspective if Z transforms are present
                DEFAULT_PERSPECTIVE / density
            } else {
                // Standard default for 3D rotations
                8f * density
            }

            // Calculate depth-based scale adjustment from translateZ
            // Positive Z = toward viewer = larger, Negative Z = away = smaller
            val depthScaleFactor = if (perspectiveDistance > 0 && totalTranslationZ != 0f) {
                val factor = 1f + (totalTranslationZ / perspectiveDistance)
                factor.coerceIn(0.1f, 10f)
            } else if (totalTranslationZ != 0f) {
                // Use default perspective for calculation
                val factor = 1f + (totalTranslationZ / DEFAULT_PERSPECTIVE)
                factor.coerceIn(0.1f, 10f)
            } else {
                1f
            }

            // Apply accumulated transforms
            translationX = totalTranslationX
            translationY = totalTranslationY
            rotationZ = totalRotation
            rotationX = totalRotationX
            rotationY = totalRotationY

            // Apply camera distance for perspective effect
            if (perspectiveDistance > 0 || totalTranslationZ != 0f || totalRotationX != 0f || totalRotationY != 0f) {
                cameraDistance = effectivePerspective
            }

            // Apply 2D scale with depth adjustment
            // scaleZ affects the perceived depth during 3D rotations
            val zAdjustedScaleX = totalScaleX * depthScaleFactor
            val zAdjustedScaleY = totalScaleY * depthScaleFactor

            // When there's 3D rotation, scaleZ affects how much the element
            // appears to stretch along the axis perpendicular to the rotation
            scaleX = if (totalRotationY != 0f && totalScaleZ != 1f) {
                // Y rotation affects perceived X width
                val rotRad = Math.toRadians(totalRotationY.toDouble())
                val cosRot = cos(rotRad).toFloat()
                zAdjustedScaleX * abs(cosRot * totalScaleZ).coerceAtLeast(0.01f)
            } else {
                zAdjustedScaleX
            }

            scaleY = if (totalRotationX != 0f && totalScaleZ != 1f) {
                // X rotation affects perceived Y height
                val rotRad = Math.toRadians(totalRotationX.toDouble())
                val cosRot = cos(rotRad).toFloat()
                zAdjustedScaleY * abs(cosRot * totalScaleZ).coerceAtLeast(0.01f)
            } else {
                zAdjustedScaleY
            }
        }
    }

    /**
     * Apply transforms using simple Modifiers (offset, rotate, scale).
     *
     * This is more efficient but doesn't support:
     * - Custom transform origin
     * - Skew
     * - 3D transforms
     */
    private fun applySimpleTransforms(modifier: Modifier, config: TransformConfig): Modifier {
        var result = modifier

        // Apply translate
        if (config.hasTranslate) {
            val x = config.translateX ?: 0.dp
            val y = config.translateY ?: 0.dp
            result = result.offset(x = x, y = y)
        }

        // Apply rotation
        config.rotate?.let { degrees ->
            result = result.rotate(degrees)
        }

        // Apply scale
        if (config.hasScale) {
            val uniformScale = config.scale ?: 1f
            val scaleX = config.scaleX ?: uniformScale
            val scaleY = config.scaleY ?: uniformScale
            result = result.scale(scaleX = scaleX, scaleY = scaleY)
        }

        return result
    }

    /**
     * Apply transforms using graphicsLayer.
     *
     * Required for:
     * - Custom transform origin
     * - 3D transforms
     * - Combined transforms with specific ordering
     */
    private fun applyWithGraphicsLayer(modifier: Modifier, config: TransformConfig): Modifier {
        return modifier.graphicsLayer {
            // Set transform origin. CSS lengths on transform-origin are
            // resolved against the element's own size; we have `size` in
            // scope here so a Dp override (config.originXDp/originYDp)
            // becomes an exact 0..1 fraction. Falls back to the fractional
            // origin when no Dp override was extracted.
            val ox = config.originXDp?.let { dp -> (dp.toPx() / size.width.coerceAtLeast(1f)) } ?: config.originX
            val oy = config.originYDp?.let { dp -> (dp.toPx() / size.height.coerceAtLeast(1f)) } ?: config.originY
            transformOrigin = TransformOrigin(ox, oy)

            // Apply translations. CSS percentage translates resolve against
            // the element's own size — `size` is available inside the
            // graphicsLayer block, so we add the fractional contribution to
            // the absolute Dp contribution instead of folding it into a
            // single value at extract time.
            translationX = (config.translateX?.toPx() ?: 0f) +
                    (config.translateXFraction?.let { size.width * it } ?: 0f)
            translationY = (config.translateY?.toPx() ?: 0f) +
                    (config.translateYFraction?.let { size.height * it } ?: 0f)

            // Apply rotations
            config.rotate?.let { rotationZ = it }
            config.rotateX?.let { rotationX = it }
            config.rotateY?.let { rotationY = it }

            // Calculate perspective
            val perspectivePx = config.perspective?.toPx() ?: DEFAULT_PERSPECTIVE

            // Calculate depth scale from translateZ
            val translateZPx = config.translateZ?.toPx() ?: 0f
            val depthScale = if (translateZPx != 0f) {
                (1f + translateZPx / perspectivePx).coerceIn(0.1f, 10f)
            } else {
                1f
            }

            // Apply scales with depth adjustment
            val uniformScale = config.scale ?: 1f
            val baseScaleX = config.scaleX ?: uniformScale
            val baseScaleY = config.scaleY ?: uniformScale
            val scaleZValue = config.scaleZ ?: 1f

            // When rotateY is set, scaleZ affects X dimension
            scaleX = if (config.rotateY != null && scaleZValue != 1f) {
                val rotRad = Math.toRadians((config.rotateY).toDouble())
                baseScaleX * depthScale * abs(cos(rotRad).toFloat() * scaleZValue).coerceAtLeast(0.01f)
            } else {
                baseScaleX * depthScale
            }

            // When rotateX is set, scaleZ affects Y dimension
            scaleY = if (config.rotateX != null && scaleZValue != 1f) {
                val rotRad = Math.toRadians((config.rotateX).toDouble())
                baseScaleY * depthScale * abs(cos(rotRad).toFloat() * scaleZValue).coerceAtLeast(0.01f)
            } else {
                baseScaleY * depthScale
            }

            // Apply perspective (using cameraDistance)
            if (config.perspective != null || config.translateZ != null ||
                config.rotateX != null || config.rotateY != null) {
                cameraDistance = perspectivePx / density
            }
        }
    }

    /**
     * Apply only translation to a Modifier.
     *
     * @param modifier The base modifier
     * @param x Horizontal offset
     * @param y Vertical offset
     * @return Modified Modifier with offset applied
     */
    fun applyTranslate(modifier: Modifier, x: Dp?, y: Dp?): Modifier {
        val dx = x ?: 0.dp
        val dy = y ?: 0.dp
        return if (dx.value != 0f || dy.value != 0f) {
            modifier.offset(x = dx, y = dy)
        } else {
            modifier
        }
    }

    /**
     * Apply only rotation to a Modifier.
     *
     * @param modifier The base modifier
     * @param degrees Rotation angle in degrees
     * @return Modified Modifier with rotation applied
     */
    fun applyRotation(modifier: Modifier, degrees: Float?): Modifier {
        return degrees?.let { modifier.rotate(it) } ?: modifier
    }

    /**
     * Apply rotation with custom origin.
     *
     * @param modifier The base modifier
     * @param degrees Rotation angle in degrees
     * @param originX Transform origin X (0-1)
     * @param originY Transform origin Y (0-1)
     * @return Modified Modifier with rotation applied
     */
    fun applyRotation(
        modifier: Modifier,
        degrees: Float,
        originX: Float,
        originY: Float
    ): Modifier {
        return modifier.graphicsLayer {
            transformOrigin = TransformOrigin(originX, originY)
            rotationZ = degrees
        }
    }

    /**
     * Apply only scale to a Modifier.
     *
     * @param modifier The base modifier
     * @param scaleX Horizontal scale factor
     * @param scaleY Vertical scale factor
     * @return Modified Modifier with scale applied
     */
    fun applyScale(modifier: Modifier, scaleX: Float?, scaleY: Float?): Modifier {
        val sx = scaleX ?: 1f
        val sy = scaleY ?: sx
        return if (sx != 1f || sy != 1f) {
            modifier.scale(scaleX = sx, scaleY = sy)
        } else {
            modifier
        }
    }

    /**
     * Apply scale with custom origin.
     *
     * @param modifier The base modifier
     * @param scaleX Horizontal scale factor
     * @param scaleY Vertical scale factor
     * @param originX Transform origin X (0-1)
     * @param originY Transform origin Y (0-1)
     * @return Modified Modifier with scale applied
     */
    fun applyScale(
        modifier: Modifier,
        scaleX: Float,
        scaleY: Float,
        originX: Float,
        originY: Float
    ): Modifier {
        return modifier.graphicsLayer {
            transformOrigin = TransformOrigin(originX, originY)
            this.scaleX = scaleX
            this.scaleY = scaleY
        }
    }

    /**
     * Apply skew transform.
     *
     * @param modifier The base modifier
     * @param skewXDegrees Skew angle on X axis in degrees
     * @param skewYDegrees Skew angle on Y axis in degrees
     * @param originX Transform origin X (0-1)
     * @param originY Transform origin Y (0-1)
     * @return Modified Modifier with skew applied
     */
    fun applySkew(
        modifier: Modifier,
        skewXDegrees: Float,
        skewYDegrees: Float = 0f,
        originX: Float = 0.5f,
        originY: Float = 0.5f
    ): Modifier {
        val skewXRad = Math.toRadians(skewXDegrees.toDouble()).toFloat()
        val skewYRad = Math.toRadians(skewYDegrees.toDouble()).toFloat()

        return modifier.drawWithContent {
            val pivotX = size.width * originX
            val pivotY = size.height * originY

            drawContext.canvas.let { canvas ->
                canvas.save()
                canvas.translate(pivotX, pivotY)
                canvas.skew(tan(skewXRad), tan(skewYRad))
                canvas.translate(-pivotX, -pivotY)
                this@drawWithContent.drawContent()
                canvas.restore()
            }
        }
    }

    /**
     * Apply translateZ transform with depth simulation.
     *
     * CSS translateZ moves elements along the Z-axis. In 2D rendering,
     * we simulate this by scaling: elements closer to the viewer appear
     * larger, elements farther away appear smaller.
     *
     * @param modifier The base modifier
     * @param translateZ Z-axis translation
     * @param perspective Perspective distance (default 1000dp)
     * @return Modified Modifier with depth simulation
     */
    fun applyTranslateZ(
        modifier: Modifier,
        translateZ: Dp,
        perspective: Dp = DEFAULT_PERSPECTIVE.dp
    ): Modifier {
        val zValue = translateZ.value
        if (zValue == 0f) return modifier

        return modifier.graphicsLayer {
            val perspectivePx = perspective.toPx()
            val zPx = translateZ.toPx()

            // Calculate scale factor based on depth
            // Positive Z = toward viewer = larger
            // Negative Z = away from viewer = smaller
            val depthScale = (1f + zPx / perspectivePx).coerceIn(0.1f, 10f)

            scaleX = depthScale
            scaleY = depthScale

            // Also adjust camera distance for proper 3D effect
            cameraDistance = perspectivePx / density
        }
    }

    /**
     * Apply scaleZ transform with 3D rotation simulation.
     *
     * CSS scaleZ affects how elements stretch along the Z-axis during
     * 3D rotations. In 2D, we simulate by adjusting the apparent scale
     * based on rotation angles.
     *
     * @param modifier The base modifier
     * @param scaleZ Z-axis scale factor
     * @param rotationX Current X rotation (affects Y dimension)
     * @param rotationY Current Y rotation (affects X dimension)
     * @return Modified Modifier with scaleZ simulation
     */
    fun applyScaleZ(
        modifier: Modifier,
        scaleZ: Float,
        rotationX: Float = 0f,
        rotationY: Float = 0f
    ): Modifier {
        if (scaleZ == 1f && rotationX == 0f && rotationY == 0f) return modifier

        return modifier.graphicsLayer {
            this.rotationX = rotationX
            this.rotationY = rotationY

            // scaleZ affects the apparent scale during rotation
            // When rotated around Y, it affects X scale
            if (rotationY != 0f) {
                val rotRad = Math.toRadians(rotationY.toDouble())
                val cosRot = cos(rotRad).toFloat()
                scaleX = abs(cosRot * scaleZ).coerceAtLeast(0.01f)
            }

            // When rotated around X, it affects Y scale
            if (rotationX != 0f) {
                val rotRad = Math.toRadians(rotationX.toDouble())
                val cosRot = cos(rotRad).toFloat()
                scaleY = abs(cosRot * scaleZ).coerceAtLeast(0.01f)
            }
        }
    }

    /**
     * Apply full 3D transform with translateZ and scaleZ.
     *
     * Combines all 3D transform effects into a single graphicsLayer call.
     *
     * @param modifier The base modifier
     * @param translateZ Z-axis translation
     * @param scaleZ Z-axis scale
     * @param rotationX X-axis rotation in degrees
     * @param rotationY Y-axis rotation in degrees
     * @param rotationZ Z-axis rotation in degrees
     * @param perspective Perspective distance
     * @return Modified Modifier with full 3D simulation
     */
    fun apply3DTransform(
        modifier: Modifier,
        translateZ: Dp = 0.dp,
        scaleZ: Float = 1f,
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        rotationZ: Float = 0f,
        perspective: Dp = DEFAULT_PERSPECTIVE.dp
    ): Modifier {
        return modifier.graphicsLayer {
            val perspectivePx = perspective.toPx()
            val zPx = translateZ.toPx()

            // Camera distance for perspective effect
            cameraDistance = perspectivePx / density

            // Apply rotations
            this.rotationX = rotationX
            this.rotationY = rotationY
            this.rotationZ = rotationZ

            // Calculate depth-based scale from translateZ
            val depthScale = if (zPx != 0f) {
                (1f + zPx / perspectivePx).coerceIn(0.1f, 10f)
            } else {
                1f
            }

            // Apply scale with scaleZ adjustments for 3D rotation
            scaleX = if (rotationY != 0f && scaleZ != 1f) {
                val rotRad = Math.toRadians(rotationY.toDouble())
                depthScale * abs(cos(rotRad).toFloat() * scaleZ).coerceAtLeast(0.01f)
            } else {
                depthScale
            }

            scaleY = if (rotationX != 0f && scaleZ != 1f) {
                val rotRad = Math.toRadians(rotationX.toDouble())
                depthScale * abs(cos(rotRad).toFloat() * scaleZ).coerceAtLeast(0.01f)
            } else {
                depthScale
            }
        }
    }

    /**
     * Documentation about 3D transform simulation in Compose.
     */
    object Notes {
        const val TRANSLATE_Z = """
            CSS translateZ moves elements along the Z-axis (toward/away from viewer).

            In true 3D, this affects rendering order and perspective distortion.
            In Compose's 2D rendering, we simulate by:

            1. Scale adjustment: translateZ > 0 makes elements larger (closer)
                                 translateZ < 0 makes elements smaller (farther)
            2. Formula: scale = 1 + (translateZ / perspective)
            3. Clamped to 0.1-10x to prevent extreme distortion

            Combined with perspective, this gives a reasonable 3D approximation.
        """

        const val SCALE_Z = """
            CSS scaleZ scales elements along the Z-axis.

            By itself, scaleZ has no visible effect in 2D.
            However, combined with 3D rotation (rotateX/rotateY), it affects
            how "thick" an element appears during rotation.

            Implementation:
            - During rotateY, scaleZ modifies the X dimension
            - During rotateX, scaleZ modifies the Y dimension
            - Effect: cos(rotation) * scaleZ determines visible dimension

            This is an approximation - true 3D would require WebGL/OpenGL.
        """

        const val PERSPECTIVE = """
            CSS perspective defines the distance between viewer and z=0 plane.

            - Larger perspective = less distortion (flatter appearance)
            - Smaller perspective = more distortion (dramatic 3D effect)

            Compose uses cameraDistance (in dp) for similar effect.
            Rough conversion: cameraDistance ≈ perspective(px) / 8

            We use DEFAULT_PERSPECTIVE (1000dp) when translateZ/scaleZ is used
            but no explicit perspective is set.
        """
    }
}
