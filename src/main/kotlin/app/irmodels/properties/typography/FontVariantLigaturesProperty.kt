package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontVariantLigaturesValue {
    NORMAL,
    NONE,
    COMMON_LIGATURES,
    NO_COMMON_LIGATURES,
    DISCRETIONARY_LIGATURES,
    NO_DISCRETIONARY_LIGATURES,
    HISTORICAL_LIGATURES,
    NO_HISTORICAL_LIGATURES,
    CONTEXTUAL,
    NO_CONTEXTUAL
}

/**
 * Represents the CSS `font-variant-ligatures` property.
 * Controls which ligatures are used.
 */
@Serializable
data class FontVariantLigaturesProperty(
    val values: List<FontVariantLigaturesValue>
) : IRProperty {
    override val propertyName = "font-variant-ligatures"
}
