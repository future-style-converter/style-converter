package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `padding-right` property.
 */
@Serializable
data class PaddingRightProperty(
    val padding: PaddingValue
) : IRProperty {
    override val propertyName = "padding-right"
}
