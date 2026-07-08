package app.irmodels.properties.borders

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class CornerShapeValue {
    ROUND, ANGLE, NOTCH, BEVEL, SCOOP, SQUIRCLE
}

/**
 * Represents the CSS `corner-shape` property.
 * Controls the shape of element corners.
 */
@Serializable
data class CornerShapeProperty(
    val value: CornerShapeValue
) : IRProperty {
    override val propertyName = "corner-shape"
}
