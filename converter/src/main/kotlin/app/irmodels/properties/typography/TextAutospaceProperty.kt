package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextAutospaceValue {
    NORMAL,
    NO_AUTOSPACE,
    IDEOGRAPH_ALPHA,
    IDEOGRAPH_NUMERIC,
    IDEOGRAPH_PARENTHESIS,
    IDEOGRAPH_SPACE
}

/**
 * Represents the CSS `text-autospace` property.
 * Controls spacing around ideographic characters (CJK).
 */
@Serializable
data class TextAutospaceProperty(
    val values: List<TextAutospaceValue>
) : IRProperty {
    override val propertyName = "text-autospace"
}
