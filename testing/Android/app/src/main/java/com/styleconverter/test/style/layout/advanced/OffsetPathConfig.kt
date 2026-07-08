package com.styleconverter.test.style.layout.advanced

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Offset path value - defines the path an element follows.
 */
sealed interface OffsetPathValue {
    data object None : OffsetPathValue
    data class Path(val d: String) : OffsetPathValue
    data class Url(val url: String) : OffsetPathValue
    data class Ray(
        val angle: Float,
        val size: RaySizeValue = RaySizeValue.CLOSEST_SIDE,
        val contain: Boolean = false
    ) : OffsetPathValue
    data class Circle(val radius: Dp? = null) : OffsetPathValue
    data class Ellipse(val radiusX: Dp? = null, val radiusY: Dp? = null) : OffsetPathValue
    data class Inset(val insets: List<Dp>) : OffsetPathValue
    data class Polygon(val points: List<Pair<Float, Float>>) : OffsetPathValue
}

/**
 * Ray size value options.
 */
enum class RaySizeValue {
    CLOSEST_SIDE,
    CLOSEST_CORNER,
    FARTHEST_SIDE,
    FARTHEST_CORNER,
    SIDES
}

/**
 * Offset anchor value.
 */
sealed interface OffsetAnchorValue {
    data object Auto : OffsetAnchorValue
    data class Position(val x: Float, val y: Float) : OffsetAnchorValue
}

/**
 * Offset rotate value.
 */
sealed interface OffsetRotateValue {
    data object Auto : OffsetRotateValue
    data object AutoReverse : OffsetRotateValue
    data class Angle(val degrees: Float) : OffsetRotateValue
    data class AutoAngle(val degrees: Float) : OffsetRotateValue
}

/**
 * Configuration for CSS offset path properties.
 * Used for motion path animations.
 */
data class OffsetPathConfig(
    val offsetPath: OffsetPathValue = OffsetPathValue.None,
    val offsetDistance: Float = 0f,
    val offsetDistanceUnit: OffsetDistanceUnit = OffsetDistanceUnit.PERCENTAGE,
    val offsetRotate: OffsetRotateValue = OffsetRotateValue.Auto,
    val offsetAnchor: OffsetAnchorValue = OffsetAnchorValue.Auto,
    val offsetPosition: OffsetAnchorValue = OffsetAnchorValue.Auto
) {
    /**
     * Check if this config has any offset path properties set.
     */
    val hasOffsetPathProperties: Boolean
        get() = offsetPath != OffsetPathValue.None ||
                offsetDistance != 0f ||
                offsetRotate != OffsetRotateValue.Auto ||
                offsetAnchor != OffsetAnchorValue.Auto ||
                offsetPosition != OffsetAnchorValue.Auto
}

/**
 * Offset distance unit options.
 */
enum class OffsetDistanceUnit {
    PERCENTAGE,
    LENGTH
}
