package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-inline-end-width` property.
 */
@Serializable
data class BorderInlineEndWidthProperty(
    val width: BorderWidthValue
) : IRProperty {
    override val propertyName = "border-inline-end-width"
}
