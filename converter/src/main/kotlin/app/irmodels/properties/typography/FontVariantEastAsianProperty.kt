package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontVariantEastAsianValue {
    NORMAL,
    JIS78,
    JIS83,
    JIS90,
    JIS04,
    SIMPLIFIED,
    TRADITIONAL,
    FULL_WIDTH,
    PROPORTIONAL_WIDTH,
    RUBY
}

/**
 * Represents the CSS `font-variant-east-asian` property.
 * Controls East Asian character variants.
 */
@Serializable
data class FontVariantEastAsianProperty(
    val values: List<FontVariantEastAsianValue>
) : IRProperty {
    override val propertyName = "font-variant-east-asian"
}
