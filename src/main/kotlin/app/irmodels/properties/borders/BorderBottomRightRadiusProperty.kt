package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderBottomRightRadiusProperty(
    val horizontal: IRLength,
    val vertical: IRLength? = null
) : IRProperty {
    override val propertyName = "border-bottom-right-radius"
}
