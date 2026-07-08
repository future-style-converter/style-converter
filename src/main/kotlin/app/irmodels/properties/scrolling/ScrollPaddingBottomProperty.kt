package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-padding-bottom` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-padding-bottom: auto | <length-percentage>`
 *
 * ## Description
 * Defines the bottom padding within the optimal viewing region of the scrollport.
 *
 * ## Examples
 * ```kotlin
 * ScrollPaddingBottomProperty(padding = ScrollPaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ScrollPaddingBottomProperty(padding = ScrollPaddingValue.Auto)
 * ```
 *
 * @property padding The bottom scroll padding value
 * @see [MDN scroll-padding-bottom](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-padding-bottom)
 */
@Serializable
data class ScrollPaddingBottomProperty(
    val padding: ScrollPaddingValue
) : IRProperty {
    override val propertyName = "scroll-padding-bottom"
}
