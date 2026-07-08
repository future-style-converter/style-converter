package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextBoxTrimValue {
    NONE,
    TRIM_START,
    TRIM_END,
    TRIM_BOTH
}

/**
 * Represents the CSS `text-box-trim` property.
 * Controls trimming of text box leading space.
 */
@Serializable
data class TextBoxTrimProperty(
    val value: TextBoxTrimValue
) : IRProperty {
    override val propertyName = "text-box-trim"
}
