package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class OutlineStyleProperty(
    val style: LineStyle
) : IRProperty {
    override val propertyName = "outline-style"
}
