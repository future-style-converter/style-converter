package app.irmodels.properties.background

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `background-position-x` property.
 *
 * ## CSS Property
 * **Syntax**: `background-position-x: left | center | right | <length> | <percentage>`
 *
 * ## Description
 * Sets the horizontal position of a background image.
 *
 * @property position The horizontal position value
 * @see [MDN background-position-x](https://developer.mozilla.org/en-US/docs/Web/CSS/background-position-x)
 */
@Serializable
data class BackgroundPositionXProperty(
    val position: PositionX
) : IRProperty {
    override val propertyName = "background-position-x"

    @Serializable
    sealed interface PositionX {
        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : PositionX

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : PositionX

        @Serializable
        @SerialName("keyword")
        data class Keyword(val value: HorizontalKeyword) : PositionX

        @Serializable
        @SerialName("edge-offset")
        data class EdgeOffset(val edge: HorizontalKeyword, val offset: IRLength) : PositionX

        @Serializable
        @SerialName("edge-offset-percent")
        data class EdgeOffsetPercent(val edge: HorizontalKeyword, val offset: IRPercentage) : PositionX

        @Serializable
        @SerialName("raw")
        data class Raw(val value: String) : PositionX
    }

    enum class HorizontalKeyword {
        LEFT, CENTER, RIGHT
    }
}
