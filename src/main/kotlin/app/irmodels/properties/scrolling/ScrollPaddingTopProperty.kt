package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-padding-top` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-padding-top: auto | <length-percentage>`
 *
 * ## Description
 * Defines the top padding within the optimal viewing region of the scrollport.
 *
 * ## Examples
 * ```kotlin
 * ScrollPaddingTopProperty(padding = ScrollPaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ScrollPaddingTopProperty(padding = ScrollPaddingValue.Auto)
 * ```
 *
 * @property padding The top scroll padding value
 * @see [MDN scroll-padding-top](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-padding-top)
 */
@Serializable
data class ScrollPaddingTopProperty(
    val padding: ScrollPaddingValue
) : IRProperty {
    override val propertyName = "scroll-padding-top"
}
