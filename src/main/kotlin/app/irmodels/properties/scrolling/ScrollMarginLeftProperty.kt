package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-margin-left` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-margin-left: <length>`
 *
 * ## Description
 * Defines the left margin of the scroll snap area that is used for snapping this box to a snapport.
 *
 * ## Examples
 * ```kotlin
 * ScrollMarginLeftProperty(margin = IRLength(10.0, LengthUnit.PX))
 * ```
 *
 * @property margin The left scroll margin value
 * @see [MDN scroll-margin-left](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-margin-left)
 */
@Serializable
data class ScrollMarginLeftProperty(
    val margin: IRLength
) : IRProperty {
    override val propertyName = "scroll-margin-left"
}
