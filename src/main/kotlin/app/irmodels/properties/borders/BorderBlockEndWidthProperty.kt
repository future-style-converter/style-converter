package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-block-end-width` property.
 */
@Serializable
data class BorderBlockEndWidthProperty(
    val width: BorderWidthValue
) : IRProperty {
    override val propertyName = "border-block-end-width"
}
