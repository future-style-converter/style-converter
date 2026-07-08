package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PositionFallbackValue {
    @Serializable
    @SerialName("none")
    data object None : PositionFallbackValue

    @Serializable
    @SerialName("named")
    data class Named(val name: String) : PositionFallbackValue
}

/**
 * Represents the CSS `position-fallback` property.
 * Specifies fallback positions for anchor positioning.
 */
@Serializable
data class PositionFallbackProperty(
    val value: PositionFallbackValue
) : IRProperty {
    override val propertyName = "position-fallback"
}
