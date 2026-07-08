package app.irmodels.properties.animations

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `animation-range` shorthand property.
 * Sets both animation-range-start and animation-range-end.
 */
@Serializable
data class AnimationRangeProperty(
    val start: AnimationRangeValue,
    val end: AnimationRangeValue?
) : IRProperty {
    override val propertyName = "animation-range"
}
