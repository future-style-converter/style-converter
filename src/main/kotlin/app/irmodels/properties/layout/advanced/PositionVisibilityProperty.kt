package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class PositionVisibilityValue {
    ALWAYS,
    ANCHORS_VISIBLE,
    NO_OVERFLOW
}

/**
 * Represents the CSS `position-visibility` property.
 * Controls visibility of anchor-positioned elements.
 */
@Serializable
data class PositionVisibilityProperty(
    val value: PositionVisibilityValue
) : IRProperty {
    override val propertyName = "position-visibility"
}
