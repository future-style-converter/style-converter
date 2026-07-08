package com.styleconverter.test.style.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.min

/**
 * Applies CSS object-fit and object-position properties to images.
 *
 * ## CSS Properties
 * ```css
 * .fitted-image {
 *     object-fit: cover;
 *     object-position: center top;
 * }
 *
 * .letterboxed {
 *     object-fit: contain;
 *     object-position: center center;
 * }
 *
 * .stretched {
 *     object-fit: fill;
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS object-fit | Compose ContentScale | Behavior |
 * |----------------|---------------------|----------|
 * | fill | FillBounds | Stretch to fill, may distort |
 * | contain | Fit | Scale to fit, letterboxing |
 * | cover | Crop | Scale to cover, may crop |
 * | none | None | Natural size, may overflow |
 * | scale-down | Inside | Smaller of none or contain |
 *
 * ## Usage
 * ```kotlin
 * ObjectFitApplier.FittedImage(
 *     painter = painterResource(id = R.drawable.photo),
 *     config = objectFitConfig,
 *     modifier = Modifier.size(200.dp)
 * )
 * ```
 */
object ObjectFitApplier {

    /**
     * Apply object-fit configuration to get ContentScale.
     */
    fun getContentScale(config: ObjectFitConfig): ContentScale {
        return config.contentScale
    }

    /**
     * Apply object-fit configuration to get Alignment.
     */
    fun getAlignment(config: ObjectFitConfig): Alignment {
        return config.alignment
    }

    /**
     * Image composable with object-fit and object-position applied.
     *
     * @param painter Image painter
     * @param config Object-fit configuration
     * @param contentDescription Content description for accessibility
     * @param modifier Modifier for the image
     * @param colorFilter Optional color filter
     */
    @Composable
    fun FittedImage(
        painter: Painter,
        config: ObjectFitConfig,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        colorFilter: ColorFilter? = null
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = config.contentScale,
            alignment = config.alignment,
            modifier = modifier,
            colorFilter = colorFilter
        )
    }

    /**
     * Container that applies object-fit behavior to any content.
     *
     * Useful for video or custom drawn content that needs similar behavior.
     *
     * @param config Object-fit configuration
     * @param modifier Modifier for the container
     * @param contentAspectRatio Aspect ratio of the content (width / height)
     * @param content Content to display
     */
    @Composable
    fun ObjectFitBox(
        config: ObjectFitConfig,
        contentAspectRatio: Float,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        when (config.fit) {
            ObjectFitValue.FILL -> {
                // Fill: content stretches to fill container
                Box(
                    modifier = modifier,
                    contentAlignment = config.alignment,
                    content = content
                )
            }
            ObjectFitValue.CONTAIN -> {
                // Contain: scale to fit within container (letterboxing)
                ContainLayout(
                    contentAspectRatio = contentAspectRatio,
                    alignment = config.alignment,
                    modifier = modifier,
                    content = content
                )
            }
            ObjectFitValue.COVER -> {
                // Cover: scale to cover container (cropping)
                CoverLayout(
                    contentAspectRatio = contentAspectRatio,
                    alignment = config.alignment,
                    modifier = modifier,
                    content = content
                )
            }
            ObjectFitValue.NONE -> {
                // None: natural size, may overflow
                Box(
                    modifier = modifier.clipToBounds(),
                    contentAlignment = config.alignment,
                    content = content
                )
            }
            ObjectFitValue.SCALE_DOWN -> {
                // Scale-down: smaller of none or contain
                ScaleDownLayout(
                    contentAspectRatio = contentAspectRatio,
                    alignment = config.alignment,
                    modifier = modifier,
                    content = content
                )
            }
        }
    }

    /**
     * Layout that scales content to fit within bounds (contain behavior).
     */
    @Composable
    private fun ContainLayout(
        contentAspectRatio: Float,
        alignment: Alignment,
        modifier: Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        Layout(
            content = { Box(content = content) },
            modifier = modifier
        ) { measurables, constraints ->
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            val containerAspectRatio = containerWidth / containerHeight

            val (contentWidth, contentHeight) = if (contentAspectRatio > containerAspectRatio) {
                // Content is wider - fit to width
                containerWidth to (containerWidth / contentAspectRatio)
            } else {
                // Content is taller - fit to height
                (containerHeight * contentAspectRatio) to containerHeight
            }

            val placeable = measurables.first().measure(
                Constraints.fixed(contentWidth.toInt(), contentHeight.toInt())
            )

            layout(constraints.maxWidth, constraints.maxHeight) {
                val offset = alignment.align(
                    androidx.compose.ui.unit.IntSize(placeable.width, placeable.height),
                    androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight),
                    layoutDirection
                )
                placeable.placeRelative(offset)
            }
        }
    }

    /**
     * Layout that scales content to cover bounds (cover behavior).
     */
    @Composable
    private fun CoverLayout(
        contentAspectRatio: Float,
        alignment: Alignment,
        modifier: Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        Layout(
            content = { Box(content = content) },
            modifier = modifier.clipToBounds()
        ) { measurables, constraints ->
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            val containerAspectRatio = containerWidth / containerHeight

            val (contentWidth, contentHeight) = if (contentAspectRatio > containerAspectRatio) {
                // Content is wider - fit to height (content overflows horizontally)
                (containerHeight * contentAspectRatio) to containerHeight
            } else {
                // Content is taller - fit to width (content overflows vertically)
                containerWidth to (containerWidth / contentAspectRatio)
            }

            val placeable = measurables.first().measure(
                Constraints.fixed(contentWidth.toInt(), contentHeight.toInt())
            )

            layout(constraints.maxWidth, constraints.maxHeight) {
                val offset = alignment.align(
                    androidx.compose.ui.unit.IntSize(placeable.width, placeable.height),
                    androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight),
                    layoutDirection
                )
                placeable.placeRelative(offset)
            }
        }
    }

    /**
     * Layout that uses the smaller of none or contain (scale-down behavior).
     */
    @Composable
    private fun ScaleDownLayout(
        contentAspectRatio: Float,
        alignment: Alignment,
        modifier: Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        Layout(
            content = { Box(content = content) },
            modifier = modifier
        ) { measurables, constraints ->
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            val containerAspectRatio = containerWidth / containerHeight

            // Calculate contain size
            val (containWidth, containHeight) = if (contentAspectRatio > containerAspectRatio) {
                containerWidth to (containerWidth / contentAspectRatio)
            } else {
                (containerHeight * contentAspectRatio) to containerHeight
            }

            // Use intrinsic size if smaller, otherwise use contain size
            val measurable = measurables.first()
            val intrinsicWidth = measurable.maxIntrinsicWidth(constraints.maxHeight)
            val intrinsicHeight = measurable.maxIntrinsicHeight(constraints.maxWidth)

            val (contentWidth, contentHeight) = if (intrinsicWidth <= containWidth.toInt() &&
                intrinsicHeight <= containHeight.toInt()) {
                // Natural size is smaller - use none behavior
                intrinsicWidth.toFloat() to intrinsicHeight.toFloat()
            } else {
                // Natural size is larger - use contain behavior
                containWidth to containHeight
            }

            val placeable = measurable.measure(
                Constraints.fixed(contentWidth.toInt(), contentHeight.toInt())
            )

            layout(constraints.maxWidth, constraints.maxHeight) {
                val offset = alignment.align(
                    androidx.compose.ui.unit.IntSize(placeable.width, placeable.height),
                    androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight),
                    layoutDirection
                )
                placeable.placeRelative(offset)
            }
        }
    }

    /**
     * CSS object-position keyword mapping.
     */
    object ObjectPosition {
        val Center = Alignment.Center
        val Top = Alignment.TopCenter
        val Bottom = Alignment.BottomCenter
        val Left = Alignment.CenterStart
        val Right = Alignment.CenterEnd
        val TopLeft = Alignment.TopStart
        val TopRight = Alignment.TopEnd
        val BottomLeft = Alignment.BottomStart
        val BottomRight = Alignment.BottomEnd

        /**
         * Create alignment from percentage values.
         *
         * @param x Horizontal position (0% = left, 50% = center, 100% = right)
         * @param y Vertical position (0% = top, 50% = center, 100% = bottom)
         */
        fun fromPercent(x: Float, y: Float): Alignment {
            val biasX = (x / 50f) - 1f  // Convert 0-100 to -1..1
            val biasY = (y / 50f) - 1f

            return Alignment { size, space, _ ->
                androidx.compose.ui.unit.IntOffset(
                    x = ((space.width - size.width) * (biasX + 1) / 2).toInt(),
                    y = ((space.height - size.height) * (biasY + 1) / 2).toInt()
                )
            }
        }
    }

    /**
     * Notes about object-fit implementation.
     */
    object Notes {
        const val COMPOSE_IMAGE = """
            For standard Image composables, use the contentScale and alignment
            parameters directly. The FittedImage helper simplifies this.
        """

        const val VIDEO_CONTENT = """
            For video or other non-Image content, use ObjectFitBox with the
            content's natural aspect ratio to achieve similar behavior.
        """

        const val SCALE_DOWN = """
            scale-down is more complex as it needs to compare the natural size
            with the container size. The ScaleDownLayout handles this but
            requires knowing the content's intrinsic size.
        """
    }
}
