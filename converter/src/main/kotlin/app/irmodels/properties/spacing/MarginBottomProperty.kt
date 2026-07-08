package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `margin-bottom` property.
 */
@Serializable
data class MarginBottomProperty(
    val margin: MarginValue
) : IRProperty {
    override val propertyName = "margin-bottom"
}
