package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class BufferedRenderingValue {
    AUTO,
    DYNAMIC,
    STATIC
}

/**
 * Represents the CSS `buffered-rendering` property.
 * Provides a hint to the browser about how to optimize rendering.
 */
@Serializable
data class BufferedRenderingProperty(
    val value: BufferedRenderingValue
) : IRProperty {
    override val propertyName = "buffered-rendering"
}
