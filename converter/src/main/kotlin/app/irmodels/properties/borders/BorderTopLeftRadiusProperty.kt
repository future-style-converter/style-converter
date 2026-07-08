package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderTopLeftRadiusProperty(
    val horizontal: IRLength,
    val vertical: IRLength? = null
) : IRProperty {
    override val propertyName = "border-top-left-radius"
}
