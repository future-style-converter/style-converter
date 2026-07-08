package app.irmodels.properties.layout

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class OverlayValue {
    NONE,
    AUTO
}

/**
 * Represents the CSS `overlay` property.
 * Controls overlay behavior.
 */
@Serializable
data class OverlayProperty(
    val value: OverlayValue
) : IRProperty {
    override val propertyName = "overlay"
}
