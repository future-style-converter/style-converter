package com.styleconverter.runtime.effects.shadow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies multiple CSS box-shadows using Compose workarounds.
 *
 * ## CSS Property
 * ```css
 * .multi-shadow {
 *     box-shadow:
 *         /* Shadow 1: main drop shadow */
 *         0 4px 6px rgba(0, 0, 0, 0.1),
 *         /* Shadow 2: spread shadow */
 *         0 10px 20px rgba(0, 0, 0, 0.15),
 *         /* Shadow 3: inset shadow */
 *         inset 0 -2px 4px rgba(0, 0, 0, 0.1);
 * }
 * ```
 *
 * ## Compose Limitation
 *
 * Compose's `Modifier.shadow()` only supports a single elevation-style shadow:
 * - No offset X/Y
 * - No spread radius
 * - No inset shadows
 * - Only one shadow per element
 *
 * ## Workaround Strategies
 *
 * ### Strategy 1: Stacked Boxes
 * Nest multiple Box composables, each with its own shadow:
 * ```
 * Box(shadow3) {
 *     Box(shadow2) {
 *         Box(shadow1) {
 *             content
 *         }
 *     }
 * }
 * ```
 *
 * ### Strategy 2: Custom Drawing
 * Use `drawBehind` with native Android canvas to draw multiple shadows
 * before the content.
 *
 * ### Strategy 3: Layered Composables
 * Use a Box with ZIndex to layer shadow elements behind content.
 *
 * ## Usage
 * ```kotlin
 * MultipleShadowApplier.BoxWithShadows(
 *     shadows = listOf(
 *         BoxShadowConfig(offsetY = 4.dp, blurRadius = 6.dp, color = Color.Black.copy(alpha = 0.1f)),
 *         BoxShadowConfig(offsetY = 10.dp, blurRadius = 20.dp, color = Color.Black.copy(alpha = 0.15f))
 *     ),
 *     shape = RoundedCornerShape(8.dp)
 * ) {
 *     // Content
 * }
 * ```
 */
object MultipleShadowApplier {

    /**
     * Composable that renders content with multiple box shadows.
     *
     * Uses stacked Box approach for cleaner shadow composition.
     *
     * @param shadows List of shadow configurations (rendered back to front)
     * @param modifier Modifier for the outermost container
     * @param shape Shape for shadows and content
     * @param backgroundColor Background color for content area
     * @param content Content to render
     */
    @Composable
    fun BoxWithShadows(
        shadows: List<BoxShadowConfig>,
        modifier: Modifier = Modifier,
        shape: Shape = RoundedCornerShape(0.dp),
        backgroundColor: Color = Color.Transparent,
        content: @Composable BoxScope.() -> Unit
    ) {
        // Filter out inset shadows (handled separately)
        val outerShadows = shadows.filter { !it.inset }
        val insetShadows = shadows.filter { it.inset }

        // Calculate total padding needed for shadow overflow
        val maxSpread = outerShadows.maxOfOrNull {
            maxOf(it.blurRadius.value, it.spreadRadius.value) +
            maxOf(kotlin.math.abs(it.offsetX.value), kotlin.math.abs(it.offsetY.value))
        } ?: 0f

        Box(modifier = modifier.padding(maxSpread.dp)) {
            // Render outer shadows from back to front
            outerShadows.reversed().forEachIndexed { index, shadow ->
                ShadowLayer(
                    shadow = shadow,
                    shape = shape,
                    isBackmost = index == outerShadows.size - 1
                )
            }

            // Content with background and inset shadows
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(backgroundColor, shape)
                    .then(
                        if (insetShadows.isNotEmpty()) {
                            Modifier.drawInsetShadows(insetShadows, shape)
                        } else Modifier
                    ),
                content = content
            )
        }
    }

    /**
     * Render a single shadow layer.
     */
    @Composable
    private fun BoxScope.ShadowLayer(
        shadow: BoxShadowConfig,
        shape: Shape,
        isBackmost: Boolean
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadow.offsetX, y = shadow.offsetY)
                .drawBehind {
                    val nativePaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = shadow.color.toArgb()

                        if (shadow.blurRadius > 0.dp) {
                            maskFilter = android.graphics.BlurMaskFilter(
                                shadow.blurRadius.toPx(),
                                android.graphics.BlurMaskFilter.Blur.NORMAL
                            )
                        }
                    }

                    val spread = shadow.spreadRadius.toPx()
                    val cornerRadius = when (shape) {
                        is RoundedCornerShape -> 8f // Approximate
                        else -> 0f
                    }

                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        -spread,
                        -spread,
                        size.width + spread,
                        size.height + spread,
                        cornerRadius,
                        cornerRadius,
                        nativePaint
                    )
                }
        )
    }

    /**
     * Modifier extension to draw inset shadows.
     */
    private fun Modifier.drawInsetShadows(
        shadows: List<BoxShadowConfig>,
        shape: Shape
    ): Modifier = this.drawWithContent {
        drawContent()

        // Draw inset shadows on top of content, clipped to shape
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }

        clipPath(path) {
            shadows.forEach { shadow ->
                val nativePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = shadow.color.toArgb()

                    if (shadow.blurRadius > 0.dp) {
                        maskFilter = android.graphics.BlurMaskFilter(
                            shadow.blurRadius.toPx(),
                            android.graphics.BlurMaskFilter.Blur.INNER
                        )
                    }
                }

                val offsetX = shadow.offsetX.toPx()
                val offsetY = shadow.offsetY.toPx()
                val spread = shadow.spreadRadius.toPx()

                // Draw from outside edges to create inset effect
                drawContext.canvas.nativeCanvas.drawRect(
                    offsetX - spread - 100f,
                    offsetY - spread - 100f,
                    offsetX - spread,
                    size.height + offsetY + spread + 100f,
                    nativePaint
                )
                drawContext.canvas.nativeCanvas.drawRect(
                    size.width + offsetX + spread,
                    offsetY - spread - 100f,
                    size.width + offsetX + spread + 100f,
                    size.height + offsetY + spread + 100f,
                    nativePaint
                )
                drawContext.canvas.nativeCanvas.drawRect(
                    offsetX - spread,
                    offsetY - spread - 100f,
                    size.width + offsetX + spread,
                    offsetY - spread,
                    nativePaint
                )
                drawContext.canvas.nativeCanvas.drawRect(
                    offsetX - spread,
                    size.height + offsetY + spread,
                    size.width + offsetX + spread,
                    size.height + offsetY + spread + 100f,
                    nativePaint
                )
            }
        }
    }

    /**
     * Apply multiple shadows via modifier chain.
     *
     * Less accurate than BoxWithShadows but works with any existing layout.
     *
     * @param modifier Base modifier
     * @param shadows List of shadow configurations
     * @return Modifier with shadows drawn behind
     */
    fun applyMultipleShadows(
        modifier: Modifier,
        shadows: List<BoxShadowConfig>
    ): Modifier {
        val outerShadows = shadows.filter { !it.inset }

        return modifier.drawBehind {
            // CSS spec: first-listed shadow is on TOP. Reverse iteration so
            // later-listed shadows paint first (underneath) and earlier-
            // listed shadows end up on top — matches Chrome/Firefox/WebKit.
            outerShadows.asReversed().forEach { shadow ->
                val nativePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = shadow.color.toArgb()

                    if (shadow.blurRadius > 0.dp) {
                        maskFilter = android.graphics.BlurMaskFilter(
                            shadow.blurRadius.toPx(),
                            android.graphics.BlurMaskFilter.Blur.NORMAL
                        )
                    }
                }

                val offsetX = shadow.offsetX.toPx()
                val offsetY = shadow.offsetY.toPx()
                val spread = shadow.spreadRadius.toPx()

                drawContext.canvas.nativeCanvas.drawRect(
                    offsetX - spread,
                    offsetY - spread,
                    size.width + offsetX + spread,
                    size.height + offsetY + spread,
                    nativePaint
                )
            }
        }
    }

    /**
     * Create a neumorphism (soft UI) effect.
     *
     * Neumorphism uses two shadows: one light (top-left) and one dark (bottom-right)
     * to create a soft, raised appearance.
     *
     * @param modifier Base modifier
     * @param backgroundColor Background color (should match parent)
     * @param lightColor Light shadow color
     * @param darkColor Dark shadow color
     * @param distance Shadow distance
     * @param blur Shadow blur
     * @param intensity Shadow intensity (opacity)
     * @param shape Shape for the element
     * @return Modifier with neumorphism effect
     */
    @Composable
    fun NeumorphicBox(
        modifier: Modifier = Modifier,
        backgroundColor: Color = Color(0xFFE0E5EC),
        lightColor: Color = Color.White,
        darkColor: Color = Color(0xFFA3B1C6),
        distance: Dp = 6.dp,
        blur: Dp = 12.dp,
        intensity: Float = 0.5f,
        shape: Shape = RoundedCornerShape(12.dp),
        content: @Composable BoxScope.() -> Unit
    ) {
        BoxWithShadows(
            shadows = listOf(
                // Light shadow (top-left)
                BoxShadowConfig(
                    offsetX = -distance,
                    offsetY = -distance,
                    blurRadius = blur,
                    color = lightColor.copy(alpha = intensity)
                ),
                // Dark shadow (bottom-right)
                BoxShadowConfig(
                    offsetX = distance,
                    offsetY = distance,
                    blurRadius = blur,
                    color = darkColor.copy(alpha = intensity)
                )
            ),
            shape = shape,
            backgroundColor = backgroundColor,
            modifier = modifier,
            content = content
        )
    }

    /**
     * Create a pressed/inset neumorphism effect.
     */
    @Composable
    fun NeumorphicBoxPressed(
        modifier: Modifier = Modifier,
        backgroundColor: Color = Color(0xFFE0E5EC),
        lightColor: Color = Color.White,
        darkColor: Color = Color(0xFFA3B1C6),
        distance: Dp = 3.dp,
        blur: Dp = 6.dp,
        intensity: Float = 0.5f,
        shape: Shape = RoundedCornerShape(12.dp),
        content: @Composable BoxScope.() -> Unit
    ) {
        BoxWithShadows(
            shadows = listOf(
                // Inset dark shadow (top-left)
                BoxShadowConfig(
                    offsetX = distance,
                    offsetY = distance,
                    blurRadius = blur,
                    color = darkColor.copy(alpha = intensity),
                    inset = true
                ),
                // Inset light shadow (bottom-right)
                BoxShadowConfig(
                    offsetX = -distance,
                    offsetY = -distance,
                    blurRadius = blur,
                    color = lightColor.copy(alpha = intensity),
                    inset = true
                )
            ),
            shape = shape,
            backgroundColor = backgroundColor,
            modifier = modifier,
            content = content
        )
    }
}

/**
 * Configuration for a single box shadow.
 */
data class BoxShadowConfig(
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
    val blurRadius: Dp = 0.dp,
    val spreadRadius: Dp = 0.dp,
    val color: Color = Color.Black,
    val inset: Boolean = false
) {
    companion object {
        val None = BoxShadowConfig()

        /** Subtle elevation shadow */
        val Elevation1 = BoxShadowConfig(
            offsetY = 1.dp,
            blurRadius = 3.dp,
            color = Color.Black.copy(alpha = 0.12f)
        )

        /** Medium elevation shadow */
        val Elevation2 = BoxShadowConfig(
            offsetY = 3.dp,
            blurRadius = 6.dp,
            color = Color.Black.copy(alpha = 0.16f)
        )

        /** High elevation shadow */
        val Elevation3 = BoxShadowConfig(
            offsetY = 10.dp,
            blurRadius = 20.dp,
            spreadRadius = (-2).dp,
            color = Color.Black.copy(alpha = 0.2f)
        )

        /** Card-style shadow */
        val Card = BoxShadowConfig(
            offsetY = 2.dp,
            blurRadius = 8.dp,
            color = Color.Black.copy(alpha = 0.1f)
        )

        /** Button hover shadow */
        val ButtonHover = BoxShadowConfig(
            offsetY = 4.dp,
            blurRadius = 12.dp,
            color = Color.Black.copy(alpha = 0.15f)
        )

        /** Floating action button shadow */
        val FAB = listOf(
            BoxShadowConfig(offsetY = 2.dp, blurRadius = 4.dp, color = Color.Black.copy(alpha = 0.2f)),
            BoxShadowConfig(offsetY = 4.dp, blurRadius = 8.dp, color = Color.Black.copy(alpha = 0.14f))
        )
    }
}
