package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-block-start-width` property.
 */
@Serializable
data class BorderBlockStartWidthProperty(
    val width: BorderWidthValue
) : IRProperty {
    override val propertyName = "border-block-start-width"
}
