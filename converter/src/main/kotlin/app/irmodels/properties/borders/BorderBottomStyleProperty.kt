package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderBottomStyleProperty(
    val style: LineStyle
) : IRProperty {
    override val propertyName = "border-bottom-style"
}
