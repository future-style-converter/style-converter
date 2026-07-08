package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderLeftColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "border-left-color"
}
