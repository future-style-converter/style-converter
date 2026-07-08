package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextDecorationSkipInkValue {
    NONE,
    AUTO,
    ALL
}

/**
 * Represents the CSS `text-decoration-skip-ink` property.
 * Controls how underlines skip descenders.
 */
@Serializable
data class TextDecorationSkipInkProperty(
    val value: TextDecorationSkipInkValue
) : IRProperty {
    override val propertyName = "text-decoration-skip-ink"
}
