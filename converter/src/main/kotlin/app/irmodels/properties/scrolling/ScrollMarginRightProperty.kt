package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-margin-right` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-margin-right: <length>`
 *
 * ## Description
 * Defines the right margin of the scroll snap area that is used for snapping this box to a snapport.
 *
 * ## Examples
 * ```kotlin
 * ScrollMarginRightProperty(margin = IRLength(10.0, LengthUnit.PX))
 * ```
 *
 * @property margin The right scroll margin value
 * @see [MDN scroll-margin-right](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-margin-right)
 */
@Serializable
data class ScrollMarginRightProperty(
    val margin: IRLength
) : IRProperty {
    override val propertyName = "scroll-margin-right"
}
