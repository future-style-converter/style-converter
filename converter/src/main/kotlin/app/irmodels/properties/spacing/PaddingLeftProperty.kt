package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `padding-left` property.
 */
@Serializable
data class PaddingLeftProperty(
    val padding: PaddingValue
) : IRProperty {
    override val propertyName = "padding-left"
}
