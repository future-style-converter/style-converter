package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderRightColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "border-right-color"
}
