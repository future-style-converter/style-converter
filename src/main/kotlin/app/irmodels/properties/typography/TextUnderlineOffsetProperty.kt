package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `text-underline-offset` property.
 *
 * ## CSS Property
 * **Syntax**: `text-underline-offset: auto | <length> | <percentage>`
 *
 * ## Description
 * Sets the offset distance of an underline text decoration line from its original position.
 *
 * @property offset The underline offset
 * @see [MDN text-underline-offset](https://developer.mozilla.org/en-US/docs/Web/CSS/text-underline-offset)
 */
@Serializable
data class TextUnderlineOffsetProperty(
    val offset: Offset
) : IRProperty {
    override val propertyName = "text-underline-offset"

    @Serializable
    sealed interface Offset {
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : Offset

        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : Offset

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : Offset
    }
}
