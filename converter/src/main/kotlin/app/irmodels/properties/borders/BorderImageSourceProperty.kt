package app.irmodels.properties.borders

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-image-source` property.
 *
 * ## CSS Property
 * **Syntax**: `border-image-source: none | <image>`
 *
 * ## Description
 * Specifies the image to use for the border. Can be a URL, gradient, or none.
 *
 * ## Examples
 * ```kotlin
 * BorderImageSourceProperty(source = BorderImageSource.Url("border.png"))
 * BorderImageSourceProperty(source = BorderImageSource.None)
 * ```
 *
 * @property source The border image source
 * @see [MDN border-image-source](https://developer.mozilla.org/en-US/docs/Web/CSS/border-image-source)
 */
@Serializable
data class BorderImageSourceProperty(
    val source: BorderImageSource
) : IRProperty {
    override val propertyName = "border-image-source"
}

/**
 * Represents border-image-source values.
 */
@Serializable
sealed interface BorderImageSource {
    @Serializable
    @SerialName("none")
    data object None : BorderImageSource

    @Serializable
    @SerialName("url")
    data class Url(val url: String) : BorderImageSource

    @Serializable
    @SerialName("gradient")
    data class Gradient(val gradient: String) : BorderImageSource
}
