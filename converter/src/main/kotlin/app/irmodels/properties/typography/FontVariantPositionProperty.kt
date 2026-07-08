package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontVariantPositionValue {
    NORMAL,
    SUB,
    SUPER
}

/**
 * Represents the CSS `font-variant-position` property.
 * Controls use of subscript and superscript glyphs.
 */
@Serializable
data class FontVariantPositionProperty(
    val value: FontVariantPositionValue
) : IRProperty {
    override val propertyName = "font-variant-position"
}
