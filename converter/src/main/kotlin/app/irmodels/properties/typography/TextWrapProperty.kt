package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextWrapValue {
    WRAP,
    NOWRAP,
    BALANCE,
    STABLE,
    PRETTY
}

/**
 * Represents the CSS `text-wrap` property.
 * Controls text wrapping behavior.
 */
@Serializable
data class TextWrapProperty(
    val value: TextWrapValue
) : IRProperty {
    override val propertyName = "text-wrap"
}
