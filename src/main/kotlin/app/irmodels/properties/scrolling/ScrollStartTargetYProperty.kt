package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-start-target-y` property.
 * Makes an element the initial vertical scroll target.
 */
@Serializable
data class ScrollStartTargetYProperty(
    val value: ScrollStartTargetValue
) : IRProperty {
    override val propertyName = "scroll-start-target-y"
}
