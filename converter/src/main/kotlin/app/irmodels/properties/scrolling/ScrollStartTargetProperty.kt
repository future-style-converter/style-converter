package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ScrollStartTargetValue {
    NONE,
    AUTO
}

/**
 * Represents the CSS `scroll-start-target` property.
 * Makes an element the initial scroll target.
 */
@Serializable
data class ScrollStartTargetProperty(
    val value: ScrollStartTargetValue
) : IRProperty {
    override val propertyName = "scroll-start-target"
}
