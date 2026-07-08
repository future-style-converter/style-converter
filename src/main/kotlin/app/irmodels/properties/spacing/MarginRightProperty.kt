package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `margin-right` property.
 */
@Serializable
data class MarginRightProperty(
    val margin: MarginValue
) : IRProperty {
    override val propertyName = "margin-right"
}
