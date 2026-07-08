package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextWrapModeValue {
    WRAP,
    NOWRAP
}

/**
 * Represents the CSS `text-wrap-mode` property.
 * Controls whether text wraps.
 */
@Serializable
data class TextWrapModeProperty(
    val value: TextWrapModeValue
) : IRProperty {
    override val propertyName = "text-wrap-mode"
}
