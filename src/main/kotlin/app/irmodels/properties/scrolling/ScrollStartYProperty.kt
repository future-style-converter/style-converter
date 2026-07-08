package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-start-y` property.
 * Sets the initial vertical scroll position.
 */
@Serializable
data class ScrollStartYProperty(
    val value: ScrollStartValue
) : IRProperty {
    override val propertyName = "scroll-start-y"
}
