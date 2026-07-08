package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontSynthesisPositionValue {
    AUTO,
    NONE
}

/**
 * Represents the CSS `font-synthesis-position` property.
 * Controls whether subscript/superscript may be synthesized.
 */
@Serializable
data class FontSynthesisPositionProperty(
    val value: FontSynthesisPositionValue
) : IRProperty {
    override val propertyName = "font-synthesis-position"
}
