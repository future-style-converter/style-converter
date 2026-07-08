package com.styleconverter.test.style.shapes

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape outside value - defines how content flows around an element.
 */
sealed interface ShapeOutsideValue {
    data object None : ShapeOutsideValue
    data object MarginBox : ShapeOutsideValue
    data object ContentBox : ShapeOutsideValue
    data object PaddingBox : ShapeOutsideValue
    data object BorderBox : ShapeOutsideValue
    data class Inset(
        val top: Dp = 0.dp,
        val right: Dp = 0.dp,
        val bottom: Dp = 0.dp,
        val left: Dp = 0.dp,
        val borderRadius: Dp? = null
    ) : ShapeOutsideValue
    data class Circle(val radius: Dp? = null, val centerX: Float = 50f, val centerY: Float = 50f) : ShapeOutsideValue
    data class Ellipse(val radiusX: Dp? = null, val radiusY: Dp? = null, val centerX: Float = 50f, val centerY: Float = 50f) : ShapeOutsideValue
    data class Polygon(val points: List<Pair<Float, Float>>) : ShapeOutsideValue
    data class Path(val d: String) : ShapeOutsideValue
    data class Url(val url: String) : ShapeOutsideValue
}

/**
 * Shape image threshold value (0-1).
 */
data class ShapeImageThreshold(val value: Float = 0f) {
    init {
        require(value in 0f..1f) { "Shape image threshold must be between 0 and 1" }
    }
}

/**
 * Configuration for CSS shape properties.
 * Includes shape-outside, shape-margin, and shape-image-threshold.
 */
data class ShapeConfig(
    val shapeOutside: ShapeOutsideValue = ShapeOutsideValue.None,
    val shapeMargin: Dp = 0.dp,
    val shapeImageThreshold: Float = 0f
) {
    /**
     * Check if this config has any shape properties set.
     */
    val hasShapeProperties: Boolean
        get() = shapeOutside != ShapeOutsideValue.None ||
                shapeMargin != 0.dp ||
                shapeImageThreshold != 0f
}
