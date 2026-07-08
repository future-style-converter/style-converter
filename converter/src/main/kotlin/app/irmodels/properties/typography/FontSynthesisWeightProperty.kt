package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontSynthesisWeightValue {
    AUTO,
    NONE
}

/**
 * Represents the CSS `font-synthesis-weight` property.
 * Controls whether bold font weight may be synthesized.
 */
@Serializable
data class FontSynthesisWeightProperty(
    val value: FontSynthesisWeightValue
) : IRProperty {
    override val propertyName = "font-synthesis-weight"
}
