package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderInlineStartStyleProperty(
    val style: LineStyle
) : IRProperty {
    override val propertyName = "border-inline-start-style"
}
