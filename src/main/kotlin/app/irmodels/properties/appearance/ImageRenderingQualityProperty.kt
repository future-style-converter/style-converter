package app.irmodels.properties.appearance

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ImageRenderingQualityValue {
    AUTO,
    HIGH,
    LOW
}

/**
 * Represents the CSS `image-rendering-quality` property.
 * Specifies image rendering quality hint.
 */
@Serializable
data class ImageRenderingQualityProperty(
    val value: ImageRenderingQualityValue
) : IRProperty {
    override val propertyName = "image-rendering-quality"
}
