package app.irmodels.properties.borders

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BorderBlockStartColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "border-block-start-color"
}
