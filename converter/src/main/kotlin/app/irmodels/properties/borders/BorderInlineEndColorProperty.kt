package app.irmodels.properties.borders

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BorderInlineEndColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "border-inline-end-color"
}
