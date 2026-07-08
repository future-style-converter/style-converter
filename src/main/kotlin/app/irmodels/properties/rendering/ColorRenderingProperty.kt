package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ColorRenderingValue {
    AUTO,
    OPTIMIZE_SPEED,
    OPTIMIZE_QUALITY
}

/**
 * Represents the CSS `color-rendering` property (SVG).
 * Hints for color rendering quality vs speed.
 */
@Serializable
data class ColorRenderingProperty(
    val value: ColorRenderingValue
) : IRProperty {
    override val propertyName = "color-rendering"
}
