package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-right-width` property.
 */
@Serializable
data class BorderRightWidthProperty(
    val width: BorderWidthValue
) : IRProperty {
    override val propertyName = "border-right-width"
}
