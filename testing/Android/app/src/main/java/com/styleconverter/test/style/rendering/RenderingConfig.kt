package com.styleconverter.test.style.rendering

/**
 * Color rendering value options.
 */
enum class ColorRenderingValue {
    AUTO,
    OPTIMIZESPEED,
    OPTIMIZEQUALITY
}

/**
 * Color interpolation value options.
 * CSS: color-interpolation, color-interpolation-filters
 */
enum class ColorInterpolationValue {
    /** Default (sRGB for filters) */
    AUTO,
    /** Interpolate in sRGB color space */
    SRGB,
    /** Interpolate in linearRGB color space */
    LINEARRGB
}

/**
 * Image rendering value options.
 */
enum class ImageRenderingValue {
    AUTO,
    SMOOTH,
    HIGH_QUALITY,
    CRISP_EDGES,
    PIXELATED
}

/**
 * Shape rendering value options.
 */
enum class ShapeRenderingValue {
    AUTO,
    OPTIMIZESPEED,
    CRISPEDGES,
    GEOMETRICPRECISION
}

/**
 * Text rendering value options.
 */
enum class TextRenderingValue {
    AUTO,
    OPTIMIZESPEED,
    OPTIMIZELEGIBILITY,
    GEOMETRICPRECISION
}

/**
 * Configuration for CSS rendering hint properties.
 * Includes color-rendering, image-rendering, shape-rendering, text-rendering.
 */
data class RenderingConfig(
    val colorRendering: ColorRenderingValue = ColorRenderingValue.AUTO,
    val imageRendering: ImageRenderingValue = ImageRenderingValue.AUTO,
    val shapeRendering: ShapeRenderingValue = ShapeRenderingValue.AUTO,
    val textRendering: TextRenderingValue = TextRenderingValue.AUTO,
    val colorInterpolation: ColorInterpolationValue = ColorInterpolationValue.AUTO,
    val colorInterpolationFilters: ColorInterpolationValue = ColorInterpolationValue.LINEARRGB
) {
    /**
     * Check if this config has any rendering properties set.
     */
    val hasRenderingProperties: Boolean
        get() = colorRendering != ColorRenderingValue.AUTO ||
                imageRendering != ImageRenderingValue.AUTO ||
                shapeRendering != ShapeRenderingValue.AUTO ||
                textRendering != TextRenderingValue.AUTO ||
                colorInterpolation != ColorInterpolationValue.AUTO ||
                colorInterpolationFilters != ColorInterpolationValue.LINEARRGB
}
