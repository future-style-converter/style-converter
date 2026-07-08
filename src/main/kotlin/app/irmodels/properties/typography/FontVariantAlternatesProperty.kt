package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontVariantAlternatesValue {
    NORMAL,
    HISTORICAL_FORMS,
    STYLISTIC,
    STYLESET,
    CHARACTER_VARIANT,
    SWASH,
    ORNAMENTS,
    ANNOTATION
}

/**
 * Represents the CSS `font-variant-alternates` property.
 * Controls alternate glyphs for fonts.
 */
@Serializable
data class FontVariantAlternatesProperty(
    val values: List<FontVariantAlternatesValue>
) : IRProperty {
    override val propertyName = "font-variant-alternates"
}
