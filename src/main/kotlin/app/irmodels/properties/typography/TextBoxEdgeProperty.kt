package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextBoxEdgeValue {
    LEADING,
    TEXT,
    CAP,
    EX,
    ALPHABETIC,
    IDEOGRAPHIC
}

/**
 * Represents the CSS `text-box-edge` property.
 * Controls which metrics define the text box edges.
 */
@Serializable
data class TextBoxEdgeProperty(
    val over: TextBoxEdgeValue,
    val under: TextBoxEdgeValue
) : IRProperty {
    override val propertyName = "text-box-edge"
}
