package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-margin-top` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-margin-top: <length>`
 *
 * ## Description
 * Defines the top margin of the scroll snap area that is used for snapping this box to a snapport.
 *
 * ## Examples
 * ```kotlin
 * ScrollMarginTopProperty(margin = IRLength(10.0, LengthUnit.PX))
 * ```
 *
 * @property margin The top scroll margin value
 * @see [MDN scroll-margin-top](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-margin-top)
 */
@Serializable
data class ScrollMarginTopProperty(
    val margin: IRLength
) : IRProperty {
    override val propertyName = "scroll-margin-top"
}
