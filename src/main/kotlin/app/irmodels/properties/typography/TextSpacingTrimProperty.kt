package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextSpacingTrimValue {
    NORMAL,
    TRIM_START,
    SPACE_FIRST,
    TRIM_END,
    SPACE_ALL,
    TRIM_ALL
}

/**
 * Represents the CSS `text-spacing-trim` property.
 * Controls spacing around punctuation in CJK text.
 */
@Serializable
data class TextSpacingTrimProperty(
    val value: TextSpacingTrimValue
) : IRProperty {
    override val propertyName = "text-spacing-trim"
}
