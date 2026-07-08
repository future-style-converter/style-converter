package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-start-inline` property.
 * Sets the initial inline-direction scroll position.
 */
@Serializable
data class ScrollStartInlineProperty(
    val value: ScrollStartValue
) : IRProperty {
    override val propertyName = "scroll-start-inline"
}
