package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-inline-start-width` property.
 */
@Serializable
data class BorderInlineStartWidthProperty(
    val width: BorderWidthValue
) : IRProperty {
    override val propertyName = "border-inline-start-width"
}
