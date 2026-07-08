package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `text-decoration-thickness` property.
 *
 * ## CSS Property
 * **Syntax**: `text-decoration-thickness: auto | from-font | <length> | <percentage>`
 *
 * ## Description
 * Sets the stroke thickness of text decoration lines.
 *
 * @property thickness The decoration thickness
 * @see [MDN text-decoration-thickness](https://developer.mozilla.org/en-US/docs/Web/CSS/text-decoration-thickness)
 */
@Serializable
data class TextDecorationThicknessProperty(
    val thickness: Thickness
) : IRProperty {
    override val propertyName = "text-decoration-thickness"

    @Serializable
    sealed interface Thickness {
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : Thickness

        @Serializable
        @SerialName("from-font")
        data class FromFont(val unit: Unit = Unit) : Thickness

        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : Thickness

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : Thickness
    }
}
