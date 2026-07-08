package app.irmodels.properties.layout

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ClearProperty(
    val clear: Clear
) : IRProperty {
    override val propertyName = "clear"

    enum class Clear {
        NONE,
        LEFT,
        RIGHT,
        BOTH,
        INLINE_START,
        INLINE_END
    }
}
