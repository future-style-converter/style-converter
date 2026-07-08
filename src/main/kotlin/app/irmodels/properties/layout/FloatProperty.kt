package app.irmodels.properties.layout

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FloatProperty(
    val float: Float
) : IRProperty {
    override val propertyName = "float"

    enum class Float {
        NONE,
        LEFT,
        RIGHT,
        INLINE_START,
        INLINE_END
    }
}
