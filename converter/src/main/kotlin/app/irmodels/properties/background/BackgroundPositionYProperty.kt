package app.irmodels.properties.background

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `background-position-y` property.
 *
 * ## CSS Property
 * **Syntax**: `background-position-y: top | center | bottom | <length> | <percentage>`
 *
 * ## Description
 * Sets the vertical position of a background image.
 *
 * @property position The vertical position value
 * @see [MDN background-position-y](https://developer.mozilla.org/en-US/docs/Web/CSS/background-position-y)
 */
@Serializable
data class BackgroundPositionYProperty(
    val position: PositionY
) : IRProperty {
    override val propertyName = "background-position-y"

    @Serializable
    sealed interface PositionY {
        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : PositionY

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : PositionY

        @Serializable
        @SerialName("keyword")
        data class Keyword(val value: VerticalKeyword) : PositionY

        @Serializable
        @SerialName("edge-offset")
        data class EdgeOffset(val edge: VerticalKeyword, val offset: IRLength) : PositionY

        @Serializable
        @SerialName("edge-offset-percent")
        data class EdgeOffsetPercent(val edge: VerticalKeyword, val offset: IRPercentage) : PositionY

        @Serializable
        @SerialName("raw")
        data class Raw(val value: String) : PositionY
    }

    enum class VerticalKeyword {
        TOP, CENTER, BOTTOM
    }
}
