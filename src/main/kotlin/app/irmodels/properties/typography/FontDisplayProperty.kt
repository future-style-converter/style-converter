package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontDisplayValue {
    AUTO,
    BLOCK,
    SWAP,
    FALLBACK,
    OPTIONAL
}

/**
 * Represents the CSS `font-display` property.
 * Controls how font face is displayed based on download status.
 */
@Serializable
data class FontDisplayProperty(
    val value: FontDisplayValue
) : IRProperty {
    override val propertyName = "font-display"
}
