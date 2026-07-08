package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `padding-bottom` property.
 */
@Serializable
data class PaddingBottomProperty(
    val padding: PaddingValue
) : IRProperty {
    override val propertyName = "padding-bottom"
}
