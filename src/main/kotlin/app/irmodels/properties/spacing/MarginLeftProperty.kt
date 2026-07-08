package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `margin-left` property.
 */
@Serializable
data class MarginLeftProperty(
    val margin: MarginValue
) : IRProperty {
    override val propertyName = "margin-left"
}
