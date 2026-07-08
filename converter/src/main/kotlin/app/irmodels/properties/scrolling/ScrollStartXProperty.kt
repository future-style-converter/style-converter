package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-start-x` property.
 * Sets the initial horizontal scroll position.
 */
@Serializable
data class ScrollStartXProperty(
    val value: ScrollStartValue
) : IRProperty {
    override val propertyName = "scroll-start-x"
}
