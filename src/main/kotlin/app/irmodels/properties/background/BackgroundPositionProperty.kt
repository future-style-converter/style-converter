package app.irmodels.properties.background

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `background-position` property.
 *
 * ## CSS Property
 * **Syntax**: `background-position: <position> [, <position>]*`
 *
 * ## Description
 * Sets the initial position for background images.
 *
 * @property positions List of position values (for multiple backgrounds)
 * @see [MDN background-position](https://developer.mozilla.org/en-US/docs/Web/CSS/background-position)
 */
@Serializable
data class BackgroundPositionProperty(
    val positions: List<PositionValue>
) : IRProperty {
    override val propertyName = "background-position"

    @Serializable
    sealed interface PositionValue {
        @Serializable
        @SerialName("center")
        data object Center : PositionValue

        @Serializable
        @SerialName("two-value")
        data class TwoValue(val x: EdgeValue, val y: EdgeValue) : PositionValue

        @Serializable
        @SerialName("keyword")
        data class Keyword(val keyword: String) : PositionValue

        @Serializable
        @SerialName("raw")
        data class Raw(val value: String) : PositionValue
    }

    @Serializable
    sealed interface EdgeValue {
        @Serializable
        @SerialName("top")
        data object Top : EdgeValue

        @Serializable
        @SerialName("bottom")
        data object Bottom : EdgeValue

        @Serializable
        @SerialName("left")
        data object Left : EdgeValue

        @Serializable
        @SerialName("right")
        data object Right : EdgeValue

        @Serializable
        @SerialName("center")
        data object Center : EdgeValue

        @Serializable
        @SerialName("length")
        data class Length(val length: IRLength) : EdgeValue

        @Serializable
        @SerialName("percentage")
        data class Percentage(val percentage: IRPercentage) : EdgeValue
    }
}
