package app.irmodels.properties.animations

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `animation-range-end` property.
 * Specifies the end of a scroll-driven animation range.
 */
@Serializable
data class AnimationRangeEndProperty(
    val value: AnimationRangeValue
) : IRProperty {
    override val propertyName = "animation-range-end"
}
