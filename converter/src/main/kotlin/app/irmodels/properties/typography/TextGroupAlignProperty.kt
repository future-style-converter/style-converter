package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextGroupAlignValue {
    NONE,
    START,
    END,
    LEFT,
    RIGHT,
    CENTER
}

/**
 * Represents the CSS `text-group-align` property.
 * Controls alignment of text with multiple writing modes.
 */
@Serializable
data class TextGroupAlignProperty(
    val value: TextGroupAlignValue
) : IRProperty {
    override val propertyName = "text-group-align"
}
