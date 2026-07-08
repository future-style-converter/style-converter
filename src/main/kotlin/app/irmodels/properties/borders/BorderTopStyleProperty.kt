package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderTopStyleProperty(
    val style: LineStyle
) : IRProperty {
    override val propertyName = "border-top-style"
}
