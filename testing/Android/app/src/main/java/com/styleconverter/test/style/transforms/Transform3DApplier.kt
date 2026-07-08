package com.styleconverter.test.style.transforms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Applies CSS 3D transform properties using Compose's graphicsLayer.
 *
 * ## CSS Properties
 * ```css
 * .card-3d {
 *     perspective: 1000px;
 *     perspective-origin: center center;
 *     transform-style: preserve-3d;
 * }
 *
 * .flip-card {
 *     backface-visibility: hidden;
 *     transform: rotateY(180deg);
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS Property | Compose Equivalent | Limitation |
 * |--------------|-------------------|------------|
 * | perspective | graphicsLayer.cameraDistance | Approximation |
 * | perspective-origin | TransformOrigin | Full support |
 * | transform-style: preserve-3d | Not supported | Flattened |
 * | backface-visibility | graphicsLayer.rotationY + alpha | Workaround |
 *
 * ## Implementation Details
 *
 * ### Perspective
 * CSS perspective creates a 3D space for child elements.
 * Compose uses cameraDistance which is related but different:
 * - CSS: perspective in pixels (larger = less distortion)
 * - Compose: cameraDistance in dp (larger = less distortion)
 *
 * Approximate conversion: cameraDistance ≈ perspective / density
 *
 * ### Backface Visibility
 * CSS backface-visibility: hidden hides elements rotated > 90°.
 * Compose doesn't have native support, so we:
 * 1. Check if rotation > 90° (or < -90°)
 * 2. Set alpha to 0 if backface should be hidden
 *
 * ### Transform Style
 * CSS transform-style: preserve-3d maintains 3D context for children.
 * Compose flattens all transforms, so preserve-3d is not truly supported.
 * We can approximate for simple cases using nested graphicsLayers.
 *
 * ## Usage
 * ```kotlin
 * Transform3DApplier.Perspective3DBox(
 *     config = transform3DConfig,
 *     modifier = Modifier.size(200.dp)
 * ) {
 *     // Child content with 3D transforms
 * }
 *
 * // For flip cards
 * Transform3DApplier.FlipCard(
 *     isFlipped = isFlipped,
 *     config = transform3DConfig,
 *     modifier = Modifier.size(200.dp),
 *     front = { Text("Front") },
 *     back = { Text("Back") }
 * )
 * ```
 */
object Transform3DApplier {

    /** Default camera distance that gives reasonable 3D effect */
    private const val DEFAULT_CAMERA_DISTANCE = 8f

    /**
     * Apply 3D transform configuration to a modifier.
     *
     * @param modifier Base modifier
     * @param config 3D transform configuration
     * @param rotationX Current X rotation in degrees
     * @param rotationY Current Y rotation in degrees
     * @param rotationZ Current Z rotation in degrees
     * @return Modifier with 3D transforms applied
     */
    fun apply3DTransform(
        modifier: Modifier,
        config: Transform3DConfig,
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        rotationZ: Float = 0f
    ): Modifier {
        // Calculate visibility based on backface-visibility
        val isBackfaceVisible = when (config.backfaceVisibility) {
            BackfaceVisibilityValue.VISIBLE -> true
            BackfaceVisibilityValue.HIDDEN -> {
                // Check if we're looking at the backface
                // For Y rotation: backface visible when |rotationY| > 90 and < 270
                val normalizedY = ((rotationY % 360) + 360) % 360
                val isBackY = normalizedY > 90f && normalizedY < 270f

                // For X rotation: similar logic
                val normalizedX = ((rotationX % 360) + 360) % 360
                val isBackX = normalizedX > 90f && normalizedX < 270f

                // Show if not looking at backface
                !isBackY && !isBackX
            }
        }

        // Calculate camera distance from perspective
        val cameraDistance = if (config.perspective != null) {
            // Rough conversion: CSS perspective / density-independent base
            // Higher values = less distortion
            config.perspective.value / 8f
        } else {
            DEFAULT_CAMERA_DISTANCE
        }

        // Calculate transform origin from perspective-origin percentages
        val transformOrigin = TransformOrigin(
            pivotFractionX = config.perspectiveOriginX / 100f,
            pivotFractionY = config.perspectiveOriginY / 100f
        )

        return modifier.graphicsLayer {
            this.cameraDistance = cameraDistance
            this.transformOrigin = transformOrigin
            this.rotationX = rotationX
            this.rotationY = rotationY
            this.rotationZ = rotationZ
            this.alpha = if (isBackfaceVisible) 1f else 0f
        }
    }

    /**
     * Composable that provides a 3D perspective context.
     *
     * @param config 3D transform configuration
     * @param modifier Modifier for the container
     * @param content Content to render with 3D perspective
     */
    @Composable
    fun Perspective3DBox(
        config: Transform3DConfig,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val cameraDistance = if (config.perspective != null) {
            config.perspective.value / 8f
        } else {
            DEFAULT_CAMERA_DISTANCE
        }

        val transformOrigin = TransformOrigin(
            pivotFractionX = config.perspectiveOriginX / 100f,
            pivotFractionY = config.perspectiveOriginY / 100f
        )

        Box(
            modifier = modifier.graphicsLayer {
                this.cameraDistance = cameraDistance
                this.transformOrigin = transformOrigin
            },
            content = content
        )
    }

    /**
     * Composable for a flip card with front and back sides.
     *
     * Implements CSS flip card pattern with backface-visibility: hidden.
     *
     * @param isFlipped Whether the card is flipped to show the back
     * @param config 3D transform configuration
     * @param modifier Modifier for the container
     * @param front Front side content
     * @param back Back side content
     */
    @Composable
    fun FlipCard(
        isFlipped: Boolean,
        config: Transform3DConfig = Transform3DConfig(
            perspective = 1000.dp,
            backfaceVisibility = BackfaceVisibilityValue.HIDDEN
        ),
        modifier: Modifier = Modifier,
        front: @Composable BoxScope.() -> Unit,
        back: @Composable BoxScope.() -> Unit
    ) {
        val rotationY = if (isFlipped) 180f else 0f

        val cameraDistance = if (config.perspective != null) {
            config.perspective.value / 8f
        } else {
            DEFAULT_CAMERA_DISTANCE
        }

        Box(modifier = modifier) {
            // Front side
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        this.cameraDistance = cameraDistance
                        this.rotationY = rotationY
                        // Hide when flipped (showing back)
                        this.alpha = if (isFlipped) 0f else 1f
                    },
                content = front
            )

            // Back side (pre-rotated 180°)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        this.cameraDistance = cameraDistance
                        this.rotationY = rotationY + 180f
                        // Show when flipped
                        this.alpha = if (isFlipped) 1f else 0f
                    },
                content = back
            )
        }
    }

    /**
     * Apply perspective to a modifier.
     *
     * @param modifier Base modifier
     * @param perspective Perspective distance in dp
     * @return Modifier with perspective applied
     */
    fun applyPerspective(
        modifier: Modifier,
        perspective: Dp
    ): Modifier {
        return modifier.graphicsLayer {
            cameraDistance = perspective.value / 8f
        }
    }

    /**
     * Apply perspective origin to a modifier.
     *
     * @param modifier Base modifier
     * @param originX X origin as percentage (0-100)
     * @param originY Y origin as percentage (0-100)
     * @return Modifier with perspective origin applied
     */
    fun applyPerspectiveOrigin(
        modifier: Modifier,
        originX: Float,
        originY: Float
    ): Modifier {
        return modifier.graphicsLayer {
            transformOrigin = TransformOrigin(
                pivotFractionX = originX / 100f,
                pivotFractionY = originY / 100f
            )
        }
    }

    /**
     * Apply backface visibility to a modifier.
     *
     * Note: This requires knowing the current rotation.
     *
     * @param modifier Base modifier
     * @param visibility Backface visibility setting
     * @param currentRotationY Current Y rotation in degrees
     * @param currentRotationX Current X rotation in degrees
     * @return Modifier with backface visibility applied
     */
    fun applyBackfaceVisibility(
        modifier: Modifier,
        visibility: BackfaceVisibilityValue,
        currentRotationY: Float = 0f,
        currentRotationX: Float = 0f
    ): Modifier {
        if (visibility == BackfaceVisibilityValue.VISIBLE) {
            return modifier
        }

        return modifier.drawWithContent {
            val normalizedY = ((currentRotationY % 360) + 360) % 360
            val normalizedX = ((currentRotationX % 360) + 360) % 360

            val isBackY = normalizedY > 90f && normalizedY < 270f
            val isBackX = normalizedX > 90f && normalizedX < 270f

            if (!isBackY && !isBackX) {
                drawContent()
            }
            // If looking at backface, don't draw anything
        }
    }

    /**
     * Calculate a parallax offset based on rotation.
     *
     * Creates a 3D-like parallax effect for layered content.
     *
     * @param rotationX X rotation in degrees
     * @param rotationY Y rotation in degrees
     * @param layerDepth Depth of this layer (higher = more parallax)
     * @param maxOffset Maximum offset in pixels
     * @return Pair of (offsetX, offsetY)
     */
    fun calculateParallaxOffset(
        rotationX: Float,
        rotationY: Float,
        layerDepth: Float,
        maxOffset: Float = 20f
    ): Pair<Float, Float> {
        // Convert rotation to offset based on layer depth
        // Positive rotation = negative offset for depth effect
        val offsetX = -rotationY * layerDepth * maxOffset / 45f
        val offsetY = rotationX * layerDepth * maxOffset / 45f

        return offsetX.coerceIn(-maxOffset, maxOffset) to
                offsetY.coerceIn(-maxOffset, maxOffset)
    }

    /**
     * Notes about 3D transform limitations in Compose.
     */
    object Notes {
        const val PRESERVE_3D = """
            CSS transform-style: preserve-3d creates a true 3D rendering context
            where child elements maintain their 3D positions relative to each other.

            Compose flattens all transforms, so children are always rendered flat
            relative to their parent. This means complex 3D scenes (like a cube)
            cannot be accurately replicated.

            Workaround: For simple cases like flip cards, use multiple overlapping
            elements with coordinated rotations.
        """

        const val CAMERA_DISTANCE = """
            CSS perspective and Compose cameraDistance are related but different:

            CSS: perspective is the distance from the viewer in pixels.
                 Larger values = less distortion, more realistic.

            Compose: cameraDistance is in density-independent units.
                     The default is 8, which is reasonable for most cases.

            Rough conversion: cameraDistance ≈ perspective(px) / 8
        """

        const val BACKFACE = """
            CSS backface-visibility: hidden hides elements when rotated
            more than 90 degrees away from the viewer.

            Compose doesn't have native backface detection, so we:
            1. Track the rotation angle
            2. Calculate if backface is showing (|rotation| > 90 and < 270)
            3. Set alpha to 0 to hide

            This works for flip cards but may not handle all edge cases.
        """
    }
}
