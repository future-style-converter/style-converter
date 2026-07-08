package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderBottomColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "border-bottom-color"
}
