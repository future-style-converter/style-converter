package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ScrollMarkerGroupValue {
    NONE, BEFORE, AFTER
}

/**
 * Represents the CSS `scroll-marker-group` property.
 * Controls scroll marker grouping.
 */
@Serializable
data class ScrollMarkerGroupProperty(
    val value: ScrollMarkerGroupValue
) : IRProperty {
    override val propertyName = "scroll-marker-group"
}
