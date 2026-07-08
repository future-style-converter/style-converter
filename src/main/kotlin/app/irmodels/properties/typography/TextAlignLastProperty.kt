package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TextAlignLastProperty(
    val alignment: TextAlignLast
) : IRProperty {
    override val propertyName = "text-align-last"

    enum class TextAlignLast {
        AUTO,
        START,
        END,
        LEFT,
        RIGHT,
        CENTER,
        JUSTIFY
    }
}
