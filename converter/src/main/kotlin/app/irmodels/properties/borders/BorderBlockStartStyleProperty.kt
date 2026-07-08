package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderBlockStartStyleProperty(
    val style: LineStyle
) : IRProperty {
    override val propertyName = "border-block-start-style"
}
