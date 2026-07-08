package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextSpaceTrimValue {
    NONE,
    TRIM_START,
    SPACE_FIRST,
    TRIM_END,
    SPACE_ALL
}

/**
 * Represents the CSS `text-space-trim` property.
 * Controls trimming of white space.
 */
@Serializable
data class TextSpaceTrimProperty(
    val values: List<TextSpaceTrimValue>
) : IRProperty {
    override val propertyName = "text-space-trim"
}
