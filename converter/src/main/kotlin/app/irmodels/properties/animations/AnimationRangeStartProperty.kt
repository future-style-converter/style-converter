package app.irmodels.properties.animations

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `animation-range-start` property.
 * Specifies the start of a scroll-driven animation range.
 */
@Serializable
data class AnimationRangeStartProperty(
    val value: AnimationRangeValue
) : IRProperty {
    override val propertyName = "animation-range-start"
}
