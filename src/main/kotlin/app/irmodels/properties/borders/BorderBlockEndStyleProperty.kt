package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BorderBlockEndStyleProperty(
    val style: LineStyle
) : IRProperty {
    override val propertyName = "border-block-end-style"
}
