package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class HangingPunctuationValue {
    NONE,
    FIRST,
    LAST,
    FORCE_END,
    ALLOW_END
}

/**
 * Represents the CSS `hanging-punctuation` property.
 * Controls whether punctuation marks hang outside line box.
 */
@Serializable
data class HangingPunctuationProperty(
    val values: List<HangingPunctuationValue>
) : IRProperty {
    override val propertyName = "hanging-punctuation"
}
