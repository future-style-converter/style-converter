package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-bottom-width` property.
 */
@Serializable
data class BorderBottomWidthProperty(
    val width: BorderWidthValue
) : IRProperty {
    override val propertyName = "border-bottom-width"
}
