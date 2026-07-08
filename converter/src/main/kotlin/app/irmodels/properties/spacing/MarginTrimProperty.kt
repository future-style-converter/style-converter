package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

enum class MarginTrimValue {
    NONE,              // No margin trimming
    BLOCK,             // Trim block margins
    BLOCK_START,       // Trim block-start margin
    BLOCK_END,         // Trim block-end margin
    INLINE,            // Trim inline margins
    INLINE_START,      // Trim inline-start margin
    INLINE_END         // Trim inline-end margin
}

/**
 * Represents the CSS `margin-trim` property.
 * Controls margin collapsing at the edges of containers.
 */
@Serializable
data class MarginTrimProperty(
    val value: MarginTrimValue
) : IRProperty {
    override val propertyName = "margin-trim"
}
