package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scrollbar-color` property.
 *
 * ## CSS Property
 * **Syntax**: `scrollbar-color: auto | <color> <color>`
 *
 * ## Description
 * Sets the color of the scrollbar thumb and track.
 *
 * @property colorValue The scrollbar color
 * @see [MDN scrollbar-color](https://developer.mozilla.org/en-US/docs/Web/CSS/scrollbar-color)
 */
@Serializable
data class ScrollbarColorProperty(
    val colorValue: ScrollbarColor
) : IRProperty {
    override val propertyName = "scrollbar-color"

    @Serializable
    sealed interface ScrollbarColor {
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : ScrollbarColor

        @Serializable
        @SerialName("colors")
        data class Colors(
            val thumb: IRColor,
            val track: IRColor
        ) : ScrollbarColor
    }
}
