package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-start-target-inline` property.
 * Makes an element the initial inline-direction scroll target.
 */
@Serializable
data class ScrollStartTargetInlineProperty(
    val value: ScrollStartTargetValue
) : IRProperty {
    override val propertyName = "scroll-start-target-inline"
}
