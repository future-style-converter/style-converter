package app.irmodels.properties.interactions

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `caret` shorthand property.
 * Combines caret-color and caret-shape.
 */
@Serializable
data class CaretProperty(
    val color: IRColor?,
    val shape: CaretShapeValue?
) : IRProperty {
    override val propertyName = "caret"
}
