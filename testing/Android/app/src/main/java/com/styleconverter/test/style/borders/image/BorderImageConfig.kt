package com.styleconverter.test.style.borders.image

import androidx.compose.ui.unit.Dp

/**
 * Configuration for CSS border-image properties.
 *
 * ## Supported Properties
 * - border-image-source: URL, gradient, or none
 * - border-image-slice: How to slice the image into 9 regions
 * - border-image-width: Width of the border image area
 * - border-image-outset: How far the border extends beyond the border box
 * - border-image-repeat: How edge regions are tiled
 *
 * ## Compose Mapping
 * Border images require custom Canvas drawing implementation.
 * Compose's border() modifier only supports solid colors and basic shapes.
 */
data class BorderImageConfig(
    /** The image source for the border */
    val source: BorderImageSourceValue = BorderImageSourceValue.None,
    /** Slice values for each edge */
    val sliceTop: BorderImageSliceEdge? = null,
    val sliceRight: BorderImageSliceEdge? = null,
    val sliceBottom: BorderImageSliceEdge? = null,
    val sliceLeft: BorderImageSliceEdge? = null,
    /** Whether to fill the middle of the image */
    val sliceFill: Boolean = false,
    /** Width of border image for each edge */
    val widthTop: BorderImageDimension? = null,
    val widthRight: BorderImageDimension? = null,
    val widthBottom: BorderImageDimension? = null,
    val widthLeft: BorderImageDimension? = null,
    /** Outset distance for each edge */
    val outsetTop: BorderImageDimension? = null,
    val outsetRight: BorderImageDimension? = null,
    val outsetBottom: BorderImageDimension? = null,
    val outsetLeft: BorderImageDimension? = null,
    /** How to repeat/tile horizontal edges */
    val repeatHorizontal: BorderImageRepeatValue = BorderImageRepeatValue.STRETCH,
    /** How to repeat/tile vertical edges */
    val repeatVertical: BorderImageRepeatValue = BorderImageRepeatValue.STRETCH
) {
    /** Returns true if any border-image property is set */
    val hasBorderImage: Boolean
        get() = source !is BorderImageSourceValue.None

    companion object {
        val Default = BorderImageConfig()
        val None = BorderImageConfig(source = BorderImageSourceValue.None)
    }
}

/**
 * Border image source values.
 */
sealed interface BorderImageSourceValue {
    /** No border image */
    data object None : BorderImageSourceValue
    /** URL to an image file */
    data class Url(val url: String) : BorderImageSourceValue
    /** Gradient definition */
    data class Gradient(val gradient: String) : BorderImageSourceValue
}

/**
 * A single slice edge value.
 * Can be a number (unitless) or percentage.
 */
data class BorderImageSliceEdge(
    val value: Float,
    val isPercentage: Boolean = false
)

/**
 * Border image dimension value.
 * Can be a length, percentage, number (multiplier), or auto.
 */
sealed interface BorderImageDimension {
    /** Use the slice size */
    data object Auto : BorderImageDimension
    /** Length value */
    data class Length(val value: Dp) : BorderImageDimension
    /** Percentage of border area */
    data class Percentage(val value: Float) : BorderImageDimension
    /** Multiplier of border-width */
    data class Number(val value: Float) : BorderImageDimension
}

/**
 * Border image repeat values.
 */
enum class BorderImageRepeatValue {
    /** Stretch to fill the area */
    STRETCH,
    /** Tile the image */
    REPEAT,
    /** Tile and scale to fit evenly */
    ROUND,
    /** Tile with spacing to fit evenly */
    SPACE
}
