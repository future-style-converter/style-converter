package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontSynthesisStyleValue {
    AUTO,
    NONE
}

/**
 * Represents the CSS `font-synthesis-style` property.
 * Controls whether italic font style may be synthesized.
 */
@Serializable
data class FontSynthesisStyleProperty(
    val value: FontSynthesisStyleValue
) : IRProperty {
    override val propertyName = "font-synthesis-style"
}
