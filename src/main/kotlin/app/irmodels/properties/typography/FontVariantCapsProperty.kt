package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontVariantCapsValue {
    NORMAL,
    SMALL_CAPS,
    ALL_SMALL_CAPS,
    PETITE_CAPS,
    ALL_PETITE_CAPS,
    UNICASE,
    TITLING_CAPS
}

/**
 * Represents the CSS `font-variant-caps` property.
 * Controls the use of capital letters and their appearance.
 */
@Serializable
data class FontVariantCapsProperty(
    val value: FontVariantCapsValue
) : IRProperty {
    override val propertyName = "font-variant-caps"
}
