package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `position-try` property.
 * Specifies fallback positions for anchor positioning.
 */
@Serializable
data class PositionTryProperty(
    val fallbacks: List<String>
) : IRProperty {
    override val propertyName = "position-try"
}
