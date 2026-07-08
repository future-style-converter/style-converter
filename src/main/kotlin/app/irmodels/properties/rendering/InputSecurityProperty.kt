package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class InputSecurityValue {
    AUTO,
    NONE
}

/**
 * Represents the CSS `input-security` property.
 * Controls secure input field rendering.
 */
@Serializable
data class InputSecurityProperty(
    val value: InputSecurityValue
) : IRProperty {
    override val propertyName = "input-security"
}
