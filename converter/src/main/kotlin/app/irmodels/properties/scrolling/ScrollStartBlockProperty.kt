package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-start-block` property.
 * Sets the initial block-direction scroll position.
 */
@Serializable
data class ScrollStartBlockProperty(
    val value: ScrollStartValue
) : IRProperty {
    override val propertyName = "scroll-start-block"
}
