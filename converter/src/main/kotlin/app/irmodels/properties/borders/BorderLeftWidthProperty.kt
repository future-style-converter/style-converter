package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-left-width` property.
 */
@Serializable
data class BorderLeftWidthProperty(
    val width: BorderWidthValue
) : IRProperty {
    override val propertyName = "border-left-width"
}
