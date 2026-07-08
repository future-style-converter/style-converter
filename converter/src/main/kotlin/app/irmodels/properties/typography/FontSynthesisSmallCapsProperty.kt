package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontSynthesisSmallCapsValue {
    AUTO,
    NONE
}

/**
 * Represents the CSS `font-synthesis-small-caps` property.
 * Controls whether small-caps may be synthesized.
 */
@Serializable
data class FontSynthesisSmallCapsProperty(
    val value: FontSynthesisSmallCapsValue
) : IRProperty {
    override val propertyName = "font-synthesis-small-caps"
}
