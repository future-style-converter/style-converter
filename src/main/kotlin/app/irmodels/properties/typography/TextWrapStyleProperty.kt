package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextWrapStyleValue {
    AUTO,
    BALANCE,
    STABLE,
    PRETTY
}

/**
 * Represents the CSS `text-wrap-style` property.
 * Controls how text wrapping is performed.
 */
@Serializable
data class TextWrapStyleProperty(
    val value: TextWrapStyleValue
) : IRProperty {
    override val propertyName = "text-wrap-style"
}
