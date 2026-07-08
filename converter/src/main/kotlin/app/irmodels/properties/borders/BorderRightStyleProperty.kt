package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderRightStyleProperty(
    val style: LineStyle
) : IRProperty {
    override val propertyName = "border-right-style"
}
