package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextRenderingValue {
    AUTO,
    OPTIMIZE_SPEED,
    OPTIMIZE_LEGIBILITY,
    GEOMETRIC_PRECISION
}

@Serializable
data class TextRenderingProperty(
    val value: TextRenderingValue
) : IRProperty {
    override val propertyName = "text-rendering"
}
