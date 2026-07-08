package app.irmodels.properties.images

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `image-rendering` property.
 *
 * ## CSS Property
 * **Syntax**: `image-rendering: auto | crisp-edges | pixelated`
 *
 * ## Description
 * Sets the scaling algorithm used for images.
 *
 * @property rendering The image rendering mode
 * @see [MDN image-rendering](https://developer.mozilla.org/en-US/docs/Web/CSS/image-rendering)
 */
@Serializable
data class ImageRenderingProperty(
    val rendering: ImageRendering
) : IRProperty {
    override val propertyName = "image-rendering"

    enum class ImageRendering {
        AUTO,
        CRISP_EDGES,
        PIXELATED,
        SMOOTH,
        HIGH_QUALITY
    }
}
