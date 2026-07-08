package com.styleconverter.test.style.effects.filter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS filter and backdrop-filter properties.
 *
 * Filters modify the rendering of an element or its backdrop:
 * - `filters` apply to the element itself (CSS `filter`)
 * - `backdropFilters` apply to the area behind the element (CSS `backdrop-filter`)
 *
 * ## Example
 * ```kotlin
 * val config = FilterConfig(
 *     filters = listOf(
 *         FilterFunction.Blur(4.dp),
 *         FilterFunction.Brightness(1.2f)
 *     )
 * )
 * ```
 */
data class FilterConfig(
    val filters: List<FilterFunction> = emptyList(),
    val backdropFilters: List<FilterFunction> = emptyList()
) {
    /** True if there are any filters to apply to the element. */
    val hasFilters: Boolean get() = filters.isNotEmpty()

    /** True if there are any backdrop filters to apply. */
    val hasBackdropFilters: Boolean get() = backdropFilters.isNotEmpty()

    /** True if there are any filters of either type. */
    val hasAnyFilters: Boolean get() = hasFilters || hasBackdropFilters
}

/**
 * Represents a single CSS filter function.
 *
 * ## Compose Support Matrix
 * | Filter | Support | Implementation |
 * |--------|---------|----------------|
 * | blur | Full | Modifier.blur() |
 * | opacity | Full | Modifier.alpha() |
 * | brightness | Partial | ColorMatrix |
 * | contrast | Partial | ColorMatrix |
 * | grayscale | Partial | ColorMatrix |
 * | hue-rotate | Partial | ColorMatrix |
 * | saturate | Partial | ColorMatrix |
 * | sepia | Partial | ColorMatrix |
 * | invert | Partial | ColorMatrix |
 * | drop-shadow | Limited | drawBehind |
 *
 * Note: ColorMatrix filters only affect images/content drawn with Paint.
 * True element-wide filtering requires RenderEffect (Android 12+).
 */
sealed interface FilterFunction {
    /**
     * Applies a Gaussian blur to the element.
     * @param radius The blur radius in Dp.
     */
    data class Blur(val radius: Dp) : FilterFunction

    /**
     * Adjusts the brightness of the element.
     * @param amount 1.0 = normal, <1.0 = darker, >1.0 = brighter
     */
    data class Brightness(val amount: Float) : FilterFunction

    /**
     * Adjusts the contrast of the element.
     * @param amount 1.0 = normal, <1.0 = less contrast, >1.0 = more contrast
     */
    data class Contrast(val amount: Float) : FilterFunction

    /**
     * Converts the element to grayscale.
     * @param amount 0.0 = no effect, 1.0 = fully grayscale
     */
    data class Grayscale(val amount: Float) : FilterFunction

    /**
     * Rotates the hue of all colors.
     * @param degrees The rotation angle in degrees (0-360)
     */
    data class HueRotate(val degrees: Float) : FilterFunction

    /**
     * Inverts the colors of the element.
     * @param amount 0.0 = no effect, 1.0 = fully inverted
     */
    data class Invert(val amount: Float) : FilterFunction

    /**
     * Adjusts the opacity of the element.
     * @param amount 0.0 = fully transparent, 1.0 = fully opaque
     */
    data class Opacity(val amount: Float) : FilterFunction

    /**
     * Adjusts the color saturation.
     * @param amount 0.0 = grayscale, 1.0 = normal, >1.0 = oversaturated
     */
    data class Saturate(val amount: Float) : FilterFunction

    /**
     * Applies a sepia tone effect.
     * @param amount 0.0 = no effect, 1.0 = fully sepia
     */
    data class Sepia(val amount: Float) : FilterFunction

    /**
     * Applies a drop shadow effect.
     * @param offsetX Horizontal offset
     * @param offsetY Vertical offset
     * @param blurRadius Blur radius
     * @param color Shadow color
     */
    data class DropShadow(
        val offsetX: Dp,
        val offsetY: Dp,
        val blurRadius: Dp,
        val color: Color
    ) : FilterFunction

    /** Explicitly no filter (used to override inherited filters). */
    data object None : FilterFunction
}
