package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `padding-top` property.
 */
@Serializable
data class PaddingTopProperty(
    val padding: PaddingValue
) : IRProperty {
    override val propertyName = "padding-top"
}
