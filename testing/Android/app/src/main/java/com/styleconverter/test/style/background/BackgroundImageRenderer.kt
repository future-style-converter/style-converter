package com.styleconverter.test.style.background

import com.styleconverter.test.style.color.BackgroundImageConfig
import com.styleconverter.test.style.color.BackgroundPositionConfig
import com.styleconverter.test.style.color.BackgroundRepeatConfig
import com.styleconverter.test.style.color.BackgroundSizeConfig
import com.styleconverter.test.style.color.ColorConfig
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale

/**
 * Renders CSS background-image: url() as a Compose background.
 *
 * ## CSS Properties Supported
 * - `background-image: url(...)` - Image URL
 * - `background-size: cover | contain | auto | <length>` - Sizing
 * - `background-position: <x> <y>` - Position
 * - `background-repeat: repeat | no-repeat | repeat-x | repeat-y` - Tiling
 * - `background-attachment: scroll | fixed` - Scroll behavior (limited)
 *
 * ## Usage
 * ```kotlin
 * BackgroundImageRenderer.BackgroundImageBox(
 *     url = "https://example.com/image.png",
 *     size = BackgroundSizeConfig.Cover,
 *     position = BackgroundPositionConfig.CENTER,
 *     repeat = BackgroundRepeatConfig.NO_REPEAT,
 *     modifier = Modifier.size(200.dp)
 * ) {
 *     // Content on top of background
 * }
 * ```
 */
object BackgroundImageRenderer {

    /**
     * Box with a background image from URL.
     *
     * @param url Image URL
     * @param size Background size configuration
     * @param position Background position configuration
     * @param repeat Background repeat configuration
     * @param colorFilter Optional color filter (for tinting)
     * @param modifier Modifier for the container
     * @param content Content to render on top of background
     */
    @Composable
    fun BackgroundImageBox(
        url: String,
        size: BackgroundSizeConfig = BackgroundSizeConfig.Auto,
        position: BackgroundPositionConfig = BackgroundPositionConfig(),
        repeat: BackgroundRepeatConfig = BackgroundRepeatConfig.REPEAT,
        colorFilter: ColorFilter? = null,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit = {}
    ) {
        val context = LocalContext.current

        // Determine content scale from background-size
        val contentScale = when (size) {
            is BackgroundSizeConfig.Cover -> ContentScale.Crop
            is BackgroundSizeConfig.Contain -> ContentScale.Fit
            is BackgroundSizeConfig.Auto -> ContentScale.None
            is BackgroundSizeConfig.Dimensions -> {
                // For explicit dimensions, we use FillBounds and let the modifier handle sizing
                ContentScale.FillBounds
            }
        }

        // Determine alignment from background-position
        // Convert 0-1 range to -1 to 1 bias (0.5 = center = 0 bias)
        val alignment: Alignment = BiasAlignment(
            horizontalBias = (position.x * 2) - 1,
            verticalBias = (position.y * 2) - 1
        )

        Box(modifier = modifier) {
            // Background image layer
            when (repeat) {
                BackgroundRepeatConfig.NO_REPEAT -> {
                    // Single image, positioned according to background-position
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = contentScale,
                        alignment = alignment,
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                BackgroundRepeatConfig.REPEAT,
                BackgroundRepeatConfig.REPEAT_X,
                BackgroundRepeatConfig.REPEAT_Y,
                BackgroundRepeatConfig.SPACE,
                BackgroundRepeatConfig.ROUND -> {
                    // Tiled image - use custom painter
                    TiledBackgroundImage(
                        url = url,
                        repeatX = repeat != BackgroundRepeatConfig.REPEAT_Y,
                        repeatY = repeat != BackgroundRepeatConfig.REPEAT_X,
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Content layer on top
            content()
        }
    }

    /**
     * Tiled background image using a custom painter.
     *
     * @param url Image URL
     * @param repeatX Whether to repeat horizontally
     * @param repeatY Whether to repeat vertically
     * @param colorFilter Optional color filter
     * @param modifier Modifier for the image
     */
    @Composable
    fun TiledBackgroundImage(
        url: String,
        repeatX: Boolean = true,
        repeatY: Boolean = true,
        colorFilter: ColorFilter? = null,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(url)
                .scale(Scale.FILL)
                .build()
        )

        // Only draw when the image is loaded
        val painterState = painter.state
        if (painterState is AsyncImagePainter.State.Success) {
            Box(
                modifier = modifier.drawBehind {
                    val imageWidth = painter.intrinsicSize.width
                    val imageHeight = painter.intrinsicSize.height

                    if (imageWidth <= 0 || imageHeight <= 0) return@drawBehind

                    // Calculate number of tiles needed
                    val tilesX = if (repeatX) {
                        (size.width / imageWidth).toInt() + 2
                    } else {
                        1
                    }
                    val tilesY = if (repeatY) {
                        (size.height / imageHeight).toInt() + 2
                    } else {
                        1
                    }

                    // Draw tiles
                    for (row in 0 until tilesY) {
                        for (col in 0 until tilesX) {
                            translate(
                                left = col * imageWidth,
                                top = row * imageHeight
                            ) {
                                with(painter) {
                                    draw(
                                        size = Size(imageWidth, imageHeight),
                                        colorFilter = colorFilter
                                    )
                                }
                            }
                        }
                    }
                }
            )
        } else {
            // Show empty box while loading
            Box(modifier = modifier)
        }
    }

    /**
     * Apply a background image as a Modifier.
     *
     * This version uses drawBehind to draw the background image,
     * which is more efficient for static backgrounds.
     *
     * @param modifier Base modifier
     * @param painter Pre-loaded image painter
     * @param size Background size configuration
     * @param position Background position configuration
     * @param colorFilter Optional color filter
     * @return Modifier with background image
     */
    fun Modifier.backgroundImage(
        painter: Painter,
        size: BackgroundSizeConfig = BackgroundSizeConfig.Cover,
        position: BackgroundPositionConfig = BackgroundPositionConfig.CENTER,
        colorFilter: ColorFilter? = null
    ): Modifier = this.drawBehind {
        val imageWidth = painter.intrinsicSize.width
        val imageHeight = painter.intrinsicSize.height

        if (imageWidth <= 0 || imageHeight <= 0) return@drawBehind

        // Calculate draw size based on background-size
        val (drawWidth, drawHeight) = when (size) {
            is BackgroundSizeConfig.Cover -> {
                val scale = maxOf(
                    this.size.width / imageWidth,
                    this.size.height / imageHeight
                )
                imageWidth * scale to imageHeight * scale
            }
            is BackgroundSizeConfig.Contain -> {
                val scale = minOf(
                    this.size.width / imageWidth,
                    this.size.height / imageHeight
                )
                imageWidth * scale to imageHeight * scale
            }
            is BackgroundSizeConfig.Auto -> {
                imageWidth to imageHeight
            }
            is BackgroundSizeConfig.Dimensions -> {
                val w = size.width?.toPx() ?: size.widthPercent?.let { this.size.width * it } ?: imageWidth
                val h = size.height?.toPx() ?: size.heightPercent?.let { this.size.height * it } ?: imageHeight
                w to h
            }
        }

        // Calculate position based on background-position
        val offsetX = (this.size.width - drawWidth) * position.x + position.xOffset.toPx()
        val offsetY = (this.size.height - drawHeight) * position.y + position.yOffset.toPx()

        translate(left = offsetX, top = offsetY) {
            with(painter) {
                draw(
                    size = Size(drawWidth, drawHeight),
                    colorFilter = colorFilter
                )
            }
        }
    }

    /**
     * Extract URL from BackgroundImageConfig.Url.
     */
    fun extractUrl(config: BackgroundImageConfig): String? {
        return when (config) {
            is BackgroundImageConfig.Url -> config.url
            else -> null
        }
    }

    /**
     * Check if a ColorConfig has any URL background images.
     */
    fun hasUrlBackground(config: ColorConfig): Boolean {
        return config.backgroundImages.any { it is BackgroundImageConfig.Url }
    }

    /**
     * Get all URL backgrounds from a ColorConfig.
     */
    fun getUrlBackgrounds(config: ColorConfig): List<String> {
        return config.backgroundImages
            .filterIsInstance<BackgroundImageConfig.Url>()
            .map { it.url }
    }
}
