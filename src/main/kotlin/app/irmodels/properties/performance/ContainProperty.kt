package app.irmodels.properties.performance

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ContainProperty(
    val values: List<ContainValue>
) : IRProperty {
    override val propertyName = "contain"

    enum class ContainValue {
        NONE,
        STRICT,
        CONTENT,
        SIZE,
        LAYOUT,
        STYLE,
        PAINT,
        INLINE_SIZE
    }
}
