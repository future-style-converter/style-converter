package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextAlignAllValue {
    START,
    END,
    LEFT,
    RIGHT,
    CENTER,
    JUSTIFY,
    MATCH_PARENT
}

/**
 * Represents the CSS `text-align-all` property.
 * Forces alignment for all lines including the last one.
 */
@Serializable
data class TextAlignAllProperty(
    val value: TextAlignAllValue
) : IRProperty {
    override val propertyName = "text-align-all"
}
