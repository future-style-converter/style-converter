package com.styleconverter.test.style.effects.mask

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.styleconverter.test.style.core.images.ImageCache
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies mask effects to Compose Modifiers.
 *
 * ## Compose Limitations
 * CSS masks are complex and Compose has limited native support:
 * - No direct mask-image support for URLs (implemented via Coil + BlendMode)
 * - Gradient masks use BlendMode.DstIn/DstOut for alpha-based masking
 * - Luminance mode requires ColorMatrix conversion to grayscale
 *
 * ## Implementation Strategy
 *
 * ### Gradient Masks
 * For gradient masks, we use graphicsLayer with compositingStrategy and
 * drawWithContent with BlendMode.DstIn to achieve masking effects.
 * The gradient's alpha channel determines visibility.
 *
 * ### URL Masks
 * For URL-based masks, Coil loads the image asynchronously. The image is drawn
 * with BlendMode.DstIn (alpha mode) or converted to luminance (luminance mode).
 *
 * ## Usage
 * ```kotlin
 * // For gradient masks (modifier-based)
 * val config = MaskExtractor.extractMaskConfig(properties)
 * val modifier = MaskApplier.applyMask(Modifier, config)
 *
 * // For URL masks (composable wrapper)
 * MaskApplier.MaskedBox(config) {
 *     // Content to be masked
 * }
 * ```
 */
object MaskApplier {

    /**
     * Apply mask configuration to a Modifier.
     *
     * Note: For URL-based masks, use MaskedBox composable instead.
     *
     * @param modifier The base modifier
     * @param config MaskConfig with mask properties
     * @return Modified Modifier with mask applied (where possible)
     */
    fun applyMask(modifier: Modifier, config: MaskConfig): Modifier {
        if (!config.hasMask) return modifier

        // For gradient masks, apply using modifier
        if (config.isGradient && config.gradient != null) {
            return applyGradientMask(modifier, config)
        }

        // For URL-based masks, return unchanged (use MaskedBox instead)
        // This allows the caller to detect and handle URL masks separately
        return modifier
    }

    /**
     * Composable wrapper for URL-based masks.
     *
     * Loads the mask image and applies it to the content.
     *
     * @param config MaskConfig with URL mask
     * @param modifier Modifier for the container
     * @param content Content to be masked
     */
    @Composable
    fun MaskedBox(
        config: MaskConfig,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        if (!config.hasMask) {
            Box(modifier = modifier, content = content)
            return
        }

        // For gradient masks, use modifier approach
        if (config.isGradient) {
            Box(
                modifier = modifier.then(applyGradientMask(Modifier, config)),
                content = content
            )
            return
        }

        // For URL masks, load image and apply as mask
        if (config.imageUrl != null) {
            UrlMaskedBox(
                url = config.imageUrl,
                config = config,
                modifier = modifier,
                content = content
            )
            return
        }

        // Fallback: no mask applied
        Box(modifier = modifier, content = content)
    }

    /**
     * Composable that applies a URL-based mask using Coil.
     */
    @Composable
    private fun UrlMaskedBox(
        url: String,
        config: MaskConfig,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val context = LocalContext.current

        // Load mask image using shared Coil loader if available
        val imageLoader = ImageCache.getCoilLoader()
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(url)
                .scale(Scale.FILL)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            imageLoader = imageLoader ?: coil.ImageLoader(context)
        )

        val painterState = painter.state

        // Create color filter for luminance mode
        val colorFilter = if (config.mode == MaskModeValue.LUMINANCE) {
            ColorFilter.colorMatrix(LUMINANCE_TO_ALPHA_MATRIX)
        } else {
            null
        }

        // Determine blend mode based on composite
        val blendMode = config.composite.toBlendMode()

        Box(
            modifier = modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    // Draw original content first
                    drawContent()

                    // Only apply mask when image is loaded
                    if (painterState is AsyncImagePainter.State.Success) {
                        val imageWidth = painter.intrinsicSize.width
                        val imageHeight = painter.intrinsicSize.height

                        if (imageWidth > 0 && imageHeight > 0) {
                            // Calculate mask dimensions based on size config
                            val (maskWidth, maskHeight) = calculateMaskSize(
                                config.size, size, imageWidth, imageHeight
                            )

                            // Calculate position based on position config
                            val (offsetX, offsetY) = calculateMaskPosition(
                                config.position, size, maskWidth, maskHeight
                            )

                            // Handle repeat
                            val (repeatX, repeatY) = when (config.repeat) {
                                MaskRepeatValue.REPEAT -> true to true
                                MaskRepeatValue.REPEAT_X -> true to false
                                MaskRepeatValue.REPEAT_Y -> false to true
                                else -> false to false
                            }

                            if (repeatX || repeatY) {
                                // Draw repeated tiles
                                drawRepeatedMask(
                                    painter = painter,
                                    tileWidth = maskWidth,
                                    tileHeight = maskHeight,
                                    repeatX = repeatX,
                                    repeatY = repeatY,
                                    blendMode = blendMode,
                                    colorFilter = colorFilter
                                )
                            } else {
                                // Draw single mask
                                with(painter) {
                                    translate(left = offsetX, top = offsetY) {
                                        draw(
                                            size = Size(maskWidth, maskHeight),
                                            colorFilter = colorFilter
                                        )
                                    }
                                }
                                // Apply blend mode with a full rect
                                // Note: The actual masking happens through the painter's alpha
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }

    /**
     * Apply a gradient-based mask effect.
     */
    private fun applyGradientMask(modifier: Modifier, config: MaskConfig): Modifier {
        val gradient = config.gradient ?: return modifier
        val blendMode = config.composite.toBlendMode()

        return modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                // Draw original content first
                drawContent()

                // Create and apply the mask gradient
                val maskBrush = createMaskBrush(gradient, size, config.mode)
                if (maskBrush != null) {
                    drawRect(
                        brush = maskBrush,
                        blendMode = blendMode
                    )
                }
            }
    }

    /**
     * Create a Brush from MaskGradientConfig.
     */
    private fun createMaskBrush(
        gradient: MaskGradientConfig,
        size: Size,
        mode: MaskModeValue
    ): Brush? {
        // Convert colors for luminance mode if needed
        val colorStops = when (gradient) {
            is MaskGradientConfig.Linear -> gradient.colorStops
            is MaskGradientConfig.Radial -> gradient.colorStops
            is MaskGradientConfig.Conic -> gradient.colorStops
        }.map { stop ->
            val color = if (mode == MaskModeValue.LUMINANCE) {
                convertToLuminance(stop.color)
            } else {
                stop.color
            }
            stop.position to color
        }.toTypedArray()

        if (colorStops.size < 2) return null

        return when (gradient) {
            is MaskGradientConfig.Linear -> {
                val angleRad = (90 - gradient.angle) * PI.toFloat() / 180f
                val halfWidth = size.width / 2
                val halfHeight = size.height / 2

                Brush.linearGradient(
                    colorStops = colorStops,
                    start = Offset(
                        halfWidth - cos(angleRad) * halfWidth,
                        halfHeight + sin(angleRad) * halfHeight
                    ),
                    end = Offset(
                        halfWidth + cos(angleRad) * halfWidth,
                        halfHeight - sin(angleRad) * halfHeight
                    ),
                    tileMode = if (gradient.repeating) TileMode.Repeated else TileMode.Clamp
                )
            }
            is MaskGradientConfig.Radial -> {
                val center = Offset(
                    gradient.centerX * size.width,
                    gradient.centerY * size.height
                )
                val radius = minOf(size.width, size.height) / 2

                Brush.radialGradient(
                    colorStops = colorStops,
                    center = center,
                    radius = radius,
                    tileMode = if (gradient.repeating) TileMode.Repeated else TileMode.Clamp
                )
            }
            is MaskGradientConfig.Conic -> {
                val center = Offset(
                    gradient.centerX * size.width,
                    gradient.centerY * size.height
                )

                // Note: Compose sweepGradient doesn't support starting angle or TileMode
                Brush.sweepGradient(
                    colorStops = colorStops,
                    center = center
                )
            }
        }
    }

    /**
     * Convert color to luminance-based alpha.
     * For luminance mode, the brightness of the color determines the mask value.
     */
    private fun convertToLuminance(color: Color): Color {
        // Calculate relative luminance using sRGB coefficients
        val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        return Color.White.copy(alpha = luminance * color.alpha)
    }

    /**
     * Calculate mask size based on MaskSizeValue.
     * Returns Pair(width, height).
     */
    private fun calculateMaskSize(
        sizeConfig: MaskSizeValue,
        containerSize: Size,
        imageWidth: Float,
        imageHeight: Float
    ): Pair<Float, Float> {
        return when (sizeConfig) {
            is MaskSizeValue.Auto -> imageWidth to imageHeight
            is MaskSizeValue.Cover -> {
                val scale = maxOf(
                    containerSize.width / imageWidth,
                    containerSize.height / imageHeight
                )
                imageWidth * scale to imageHeight * scale
            }
            is MaskSizeValue.Contain -> {
                val scale = minOf(
                    containerSize.width / imageWidth,
                    containerSize.height / imageHeight
                )
                imageWidth * scale to imageHeight * scale
            }
            is MaskSizeValue.Dimensions -> {
                val w = sizeConfig.width?.value ?: imageWidth
                val h = sizeConfig.height?.value ?: imageHeight
                w to h
            }
        }
    }

    /**
     * Calculate mask position based on MaskPositionValue.
     * Returns Pair(offsetX, offsetY).
     */
    private fun calculateMaskPosition(
        positionConfig: MaskPositionValue,
        containerSize: Size,
        maskWidth: Float,
        maskHeight: Float
    ): Pair<Float, Float> {
        val xFraction = when (val x = positionConfig.x) {
            is PositionComponent.Keyword -> when (x.position) {
                HorizontalPosition.LEFT -> 0f
                HorizontalPosition.CENTER -> 0.5f
                HorizontalPosition.RIGHT -> 1f
                else -> 0f
            }
            is PositionComponent.Percentage -> x.value / 100f
            is PositionComponent.Length -> x.value.value / containerSize.width
        }

        val yFraction = when (val y = positionConfig.y) {
            is PositionComponent.Keyword -> when (y.position) {
                VerticalPosition.TOP -> 0f
                VerticalPosition.CENTER -> 0.5f
                VerticalPosition.BOTTOM -> 1f
                else -> 0f
            }
            is PositionComponent.Percentage -> y.value / 100f
            is PositionComponent.Length -> y.value.value / containerSize.height
        }

        val offsetX = (containerSize.width - maskWidth) * xFraction
        val offsetY = (containerSize.height - maskHeight) * yFraction

        return offsetX to offsetY
    }

    /**
     * Draw a repeated mask pattern.
     */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRepeatedMask(
        painter: Painter,
        tileWidth: Float,
        tileHeight: Float,
        repeatX: Boolean,
        repeatY: Boolean,
        blendMode: BlendMode,
        colorFilter: ColorFilter?
    ) {
        val tilesX = if (repeatX) (size.width / tileWidth).toInt() + 2 else 1
        val tilesY = if (repeatY) (size.height / tileHeight).toInt() + 2 else 1

        for (row in 0 until tilesY) {
            for (col in 0 until tilesX) {
                translate(
                    left = col * tileWidth,
                    top = row * tileHeight
                ) {
                    with(painter) {
                        draw(
                            size = Size(tileWidth, tileHeight),
                            colorFilter = colorFilter
                        )
                    }
                }
            }
        }
    }

    /**
     * Extension to convert MaskCompositeValue to BlendMode.
     */
    private fun MaskCompositeValue.toBlendMode(): BlendMode {
        return when (this) {
            MaskCompositeValue.ADD -> BlendMode.DstIn
            MaskCompositeValue.SUBTRACT -> BlendMode.DstOut
            MaskCompositeValue.INTERSECT -> BlendMode.DstIn
            MaskCompositeValue.EXCLUDE -> BlendMode.Xor
        }
    }

    /**
     * Check if mask mode uses luminance.
     */
    fun useLuminanceMode(config: MaskConfig): Boolean {
        return config.mode == MaskModeValue.LUMINANCE
    }

    /**
     * Check if config requires the MaskedBox composable (URL-based mask).
     */
    fun requiresMaskedBox(config: MaskConfig): Boolean {
        return config.isUrlMask
    }

    /**
     * Color matrix to convert RGB to luminance-based alpha.
     * Uses sRGB luminance coefficients.
     */
    private val LUMINANCE_TO_ALPHA_MATRIX = ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, 255f,  // Red -> White
            0f, 0f, 0f, 0f, 255f,  // Green -> White
            0f, 0f, 0f, 0f, 255f,  // Blue -> White
            0.2126f, 0.7152f, 0.0722f, 0f, 0f  // RGB luminance -> Alpha
        )
    )
}

/**
 * Helper extension to apply translate within DrawScope.
 */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.translate(
    left: Float = 0f,
    top: Float = 0f,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) {
    drawContext.transform.translate(left, top)
    block()
    drawContext.transform.translate(-left, -top)
}
