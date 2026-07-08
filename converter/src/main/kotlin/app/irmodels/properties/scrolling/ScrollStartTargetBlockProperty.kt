package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-start-target-block` property.
 * Makes an element the initial block-direction scroll target.
 */
@Serializable
data class ScrollStartTargetBlockProperty(
    val value: ScrollStartTargetValue
) : IRProperty {
    override val propertyName = "scroll-start-target-block"
}
