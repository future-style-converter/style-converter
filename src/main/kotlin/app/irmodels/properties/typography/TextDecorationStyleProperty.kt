package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TextDecorationStyleProperty(
    val style: DecorationStyle
) : IRProperty {
    override val propertyName = "text-decoration-style"

    enum class DecorationStyle {
        SOLID, DOUBLE, DOTTED, DASHED, WAVY
    }
}
