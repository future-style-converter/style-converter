package app.irmodels.properties.interactions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ResizeProperty(
    val value: Resize
) : IRProperty {
    override val propertyName = "resize"

    enum class Resize {
        NONE, BOTH, HORIZONTAL, VERTICAL, BLOCK, INLINE
    }
}
