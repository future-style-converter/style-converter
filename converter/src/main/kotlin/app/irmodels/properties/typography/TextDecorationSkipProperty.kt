package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextDecorationSkipValue {
    NONE,
    OBJECTS,
    SPACES,
    LEADING_SPACES,
    TRAILING_SPACES,
    EDGES,
    BOX_DECORATION
}

/**
 * Represents the CSS `text-decoration-skip` property.
 * Controls what parts of text decoration are skipped.
 */
@Serializable
data class TextDecorationSkipProperty(
    val values: List<TextDecorationSkipValue>
) : IRProperty {
    override val propertyName = "text-decoration-skip"
}
