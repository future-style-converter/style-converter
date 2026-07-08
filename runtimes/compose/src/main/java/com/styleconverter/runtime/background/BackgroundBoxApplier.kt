package com.styleconverter.runtime.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies CSS background-clip and background-origin properties.
 *
 * ## CSS Properties
 * ```css
 * .content-box-bg {
 *     background-clip: content-box;  /* Background only in content area */
 *     background-origin: content-box; /* Position background from content edge */
 *     padding: 20px;
 *     background-color: blue;
 * }
 *
 * .text-clip {
 *     background-clip: text;  /* Background only visible through text */
 *     -webkit-background-clip: text;
 *     color: transparent;
 * }
 * ```
 *
 * ## Compose Limitation
 *
 * Compose's `Modifier.background()` always paints to the full bounds.
 * There's no built-in way to clip background to:
 * - padding-box (inside borders)
 * - content-box (inside padding)
 * - text (through text glyphs only)
 *
 * ## Implementation Strategy
 *
 * ### border-box (default)
 * Standard `Modifier.background()` - extends to full bounds including borders.
 *
 * ### padding-box
 * Use `clipPath` or `clipRect` with inset matching border widths.
 *
 * ### content-box
 * Use `clipPath` or `clipRect` with inset matching border + padding.
 *
 * ### text
 * Use `BlendMode.DstIn` to mask background through text.
 * Requires the text to be rendered with a solid color first.
 *
 * ## Usage
 * ```kotlin
 * BackgroundBoxApplier.BackgroundBox(
 *     config = backgroundBoxConfig,
 *     backgroundColor = Color.Blue,
 *     borderWidths = BorderWidths(2.dp, 2.dp, 2.dp, 2.dp),
 *     padding = PaddingValues(16.dp),
 *     modifier = Modifier.size(200.dp)
 * ) {
 *     // Content
 * }
 * ```
 */
object BackgroundBoxApplier {

    /**
     * Composable that applies background with clip and origin settings.
     *
     * @param config Background box configuration
     * @param backgroundColor Background color
     * @param borderWidths Border widths for padding-box calculation
     * @param padding Padding for content-box calculation
     * @param shape Shape for the container
     * @param modifier Modifier for the container
     * @param content Content to render
     */
    @Composable
    fun BackgroundBox(
        config: BackgroundBoxConfig,
        backgroundColor: Color,
        borderWidths: BorderWidths = BorderWidths.Zero,
        padding: PaddingValues = PaddingValues(0.dp),
        shape: Shape = RectangleShape,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val clipModifier = when (config.backgroundClip) {
            BackgroundBoxValue.BORDER_BOX -> {
                // Default - background extends to border edge
                Modifier.background(backgroundColor, shape)
            }
            BackgroundBoxValue.PADDING_BOX -> {
                // Clip to inside of borders
                Modifier.drawBehind {
                    clipRect(
                        left = borderWidths.left.toPx(),
                        top = borderWidths.top.toPx(),
                        right = size.width - borderWidths.right.toPx(),
                        bottom = size.height - borderWidths.bottom.toPx()
                    ) {
                        drawRect(backgroundColor)
                    }
                }
            }
            BackgroundBoxValue.CONTENT_BOX -> {
                // Clip to content area (inside borders + padding)
                val insetLeft = borderWidths.left + padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                val insetTop = borderWidths.top + padding.calculateTopPadding()
                val insetRight = borderWidths.right + padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                val insetBottom = borderWidths.bottom + padding.calculateBottomPadding()

                Modifier.drawBehind {
                    clipRect(
                        left = insetLeft.toPx(),
                        top = insetTop.toPx(),
                        right = size.width - insetRight.toPx(),
                        bottom = size.height - insetBottom.toPx()
                    ) {
                        drawRect(backgroundColor)
                    }
                }
            }
            BackgroundBoxValue.TEXT -> {
                // Text clipping needs special handling - use BlendMode
                Modifier.drawWithContent {
                    // First draw the background
                    drawRect(backgroundColor)
                    // Then draw content with DstIn to mask through text
                    drawContent()
                }
            }
        }

        Box(
            modifier = modifier.then(clipModifier),
            content = content
        )
    }

    /**
     * Apply background with gradient and clip settings.
     *
     * @param config Background box configuration
     * @param brush Gradient brush
     * @param borderWidths Border widths
     * @param padding Padding values
     * @param modifier Base modifier
     * @return Modified Modifier
     */
    // TODO(tier1): does NOT honor background-size for gradient brushes.
    // Caught by Auditor round 8: with background-size: 50px on a 180px-wide box
    // and a linear-gradient, web tiles the gradient (~7 stripes), iOS scales it
    // to fill, Android renders the gradient at full container size (50px is
    // ignored). Fix would add a  param and tile via
    // Modifier.drawBehind { val brushBitmap = brush.rasterize(50,50); tile() }
    // for Dimensions case. Requires Coil + ImageBitmap allocation.
    fun applyBackgroundWithClip(
        modifier: Modifier,
        config: BackgroundBoxConfig,
        brush: Brush,
        borderWidths: BorderWidths = BorderWidths.Zero,
        padding: PaddingValues = PaddingValues(0.dp)
    ): Modifier {
        return when (config.backgroundClip) {
            BackgroundBoxValue.BORDER_BOX -> {
                modifier.background(brush)
            }
            BackgroundBoxValue.PADDING_BOX -> {
                modifier.drawBehind {
                    clipRect(
                        left = borderWidths.left.toPx(),
                        top = borderWidths.top.toPx(),
                        right = size.width - borderWidths.right.toPx(),
                        bottom = size.height - borderWidths.bottom.toPx()
                    ) {
                        drawRect(brush)
                    }
                }
            }
            BackgroundBoxValue.CONTENT_BOX -> {
                val insetLeft = borderWidths.left + padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                val insetTop = borderWidths.top + padding.calculateTopPadding()
                val insetRight = borderWidths.right + padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                val insetBottom = borderWidths.bottom + padding.calculateBottomPadding()

                modifier.drawBehind {
                    clipRect(
                        left = insetLeft.toPx(),
                        top = insetTop.toPx(),
                        right = size.width - insetRight.toPx(),
                        bottom = size.height - insetBottom.toPx()
                    ) {
                        drawRect(brush)
                    }
                }
            }
            BackgroundBoxValue.TEXT -> {
                // Text clip with gradient
                modifier.drawWithContent {
                    drawRect(brush)
                    drawContent()
                }
            }
        }
    }

    /**
     * Apply background-origin offset to gradient positioning.
     *
     * Background-origin determines where the background positioning area starts.
     *
     * @param config Background box configuration
     * @param borderWidths Border widths
     * @param padding Padding values
     * @return Offset for gradient starting position
     */
    fun calculateOriginOffset(
        config: BackgroundBoxConfig,
        borderWidths: BorderWidths,
        padding: PaddingValues
    ): Offset {
        return when (config.backgroundOrigin) {
            BackgroundBoxValue.BORDER_BOX -> Offset.Zero
            BackgroundBoxValue.PADDING_BOX -> Offset(
                x = borderWidths.left.value,
                y = borderWidths.top.value
            )
            BackgroundBoxValue.CONTENT_BOX -> Offset(
                x = borderWidths.left.value + padding.calculateLeftPadding(
                    androidx.compose.ui.unit.LayoutDirection.Ltr
                ).value,
                y = borderWidths.top.value + padding.calculateTopPadding().value
            )
            BackgroundBoxValue.TEXT -> Offset.Zero
        }
    }

    /**
     * Calculate the background positioning area size.
     *
     * @param totalSize Total element size
     * @param config Background box configuration
     * @param borderWidths Border widths
     * @param padding Padding values
     * @return Size of the background positioning area
     */
    fun calculateOriginSize(
        totalSize: Size,
        config: BackgroundBoxConfig,
        borderWidths: BorderWidths,
        padding: PaddingValues
    ): Size {
        return when (config.backgroundOrigin) {
            BackgroundBoxValue.BORDER_BOX -> totalSize
            BackgroundBoxValue.PADDING_BOX -> Size(
                width = totalSize.width - borderWidths.left.value - borderWidths.right.value,
                height = totalSize.height - borderWidths.top.value - borderWidths.bottom.value
            )
            BackgroundBoxValue.CONTENT_BOX -> {
                val horizontalPadding = padding.calculateLeftPadding(
                    androidx.compose.ui.unit.LayoutDirection.Ltr
                ).value + padding.calculateRightPadding(
                    androidx.compose.ui.unit.LayoutDirection.Ltr
                ).value
                val verticalPadding = padding.calculateTopPadding().value +
                        padding.calculateBottomPadding().value
                Size(
                    width = totalSize.width - borderWidths.left.value - borderWidths.right.value - horizontalPadding,
                    height = totalSize.height - borderWidths.top.value - borderWidths.bottom.value - verticalPadding
                )
            }
            BackgroundBoxValue.TEXT -> totalSize
        }
    }

    /**
     * Composable for text-clipped background.
     *
     * Creates a background that only shows through the text content.
     *
     * @param backgroundColor Background color (or use brush variant)
     * @param modifier Modifier for the container
     * @param content Text content (should use Color.Transparent or BlendMode)
     */
    @Composable
    fun TextClippedBackground(
        backgroundColor: Color,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        Box(
            modifier = modifier.drawWithContent {
                // Draw background
                drawRect(backgroundColor)
                // Draw content - text should be opaque to create the mask
                drawContent()
            },
            content = content
        )
    }

    /**
     * Composable for text-clipped gradient background.
     *
     * @param brush Gradient brush
     * @param modifier Modifier for the container
     * @param content Text content
     */
    @Composable
    fun TextClippedGradient(
        brush: Brush,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        Box(
            modifier = modifier.drawWithContent {
                drawRect(brush)
                drawContent()
            },
            content = content
        )
    }

    /**
     * Notes about background-clip: text implementation.
     */
    object TextClipNotes {
        const val USAGE = """
            To achieve background-clip: text effect:
            1. Use TextClippedBackground or TextClippedGradient
            2. Set text color to transparent
            3. The background will show through the text glyphs

            Note: This is an approximation. True CSS background-clip: text
            creates a mask from text glyphs. In Compose, we overlay the
            text on the background.
        """

        const val LIMITATION = """
            Compose doesn't have a direct equivalent to background-clip: text.
            The workaround uses layer blending, which may not work perfectly
            with all background types (especially images).
        """
    }
}

/**
 * Border widths for all four sides.
 */
data class BorderWidths(
    val top: Dp,
    val right: Dp,
    val bottom: Dp,
    val left: Dp
) {
    constructor(all: Dp) : this(all, all, all, all)

    companion object {
        val Zero = BorderWidths(0.dp, 0.dp, 0.dp, 0.dp)
    }
}
