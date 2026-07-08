package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-start-target-x` property.
 * Makes an element the initial horizontal scroll target.
 */
@Serializable
data class ScrollStartTargetXProperty(
    val value: ScrollStartTargetValue
) : IRProperty {
    override val propertyName = "scroll-start-target-x"
}
