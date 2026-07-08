package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class WordSpaceTransformValue {
    NONE,
    AUTO,
    SPACE,
    IDEOGRAPHIC_SPACE
}

/**
 * Represents the CSS `word-space-transform` property.
 * Controls transformation of word separators.
 */
@Serializable
data class WordSpaceTransformProperty(
    val value: WordSpaceTransformValue
) : IRProperty {
    override val propertyName = "word-space-transform"
}
