package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-margin-bottom` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-margin-bottom: <length>`
 *
 * ## Description
 * Defines the bottom margin of the scroll snap area that is used for snapping this box to a snapport.
 *
 * ## Examples
 * ```kotlin
 * ScrollMarginBottomProperty(margin = IRLength(10.0, LengthUnit.PX))
 * ```
 *
 * @property margin The bottom scroll margin value
 * @see [MDN scroll-margin-bottom](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-margin-bottom)
 */
@Serializable
data class ScrollMarginBottomProperty(
    val margin: IRLength
) : IRProperty {
    override val propertyName = "scroll-margin-bottom"
}
