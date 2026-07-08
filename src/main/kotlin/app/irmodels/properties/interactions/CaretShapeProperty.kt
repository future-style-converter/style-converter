package app.irmodels.properties.interactions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class CaretShapeValue {
    AUTO, BAR, BLOCK, UNDERSCORE
}

/**
 * Represents the CSS `caret-shape` property.
 * Controls the shape of the text insertion caret.
 */
@Serializable
data class CaretShapeProperty(
    val value: CaretShapeValue
) : IRProperty {
    override val propertyName = "caret-shape"
}
