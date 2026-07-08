package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextAnchorValue {
    START,
    MIDDLE,
    END
}

/**
 * Represents the CSS `text-anchor` property (SVG).
 * Controls text anchor point for SVG text.
 */
@Serializable
data class TextAnchorProperty(
    val value: TextAnchorValue
) : IRProperty {
    override val propertyName = "text-anchor"
}
