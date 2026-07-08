package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `position-try-fallbacks` property.
 * Specifies fallback positions for anchor positioning.
 */
@Serializable
data class PositionTryFallbacksProperty(
    val fallbacks: List<String>
) : IRProperty {
    override val propertyName = "position-try-fallbacks"
}
