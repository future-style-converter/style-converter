package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderLeftStyleProperty(
    val style: LineStyle
) : IRProperty {
    override val propertyName = "border-left-style"
}
