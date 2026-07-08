package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-margin-inline-end` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-margin-inline-end: <length>`
 *
 * ## Description
 * Defines the scroll margin at the inline-end edge (right in LTR, left in RTL).
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * ScrollMarginInlineEndProperty(margin = IRLength(10.0, LengthUnit.PX))
 * ```
 *
 * @property margin The inline-end scroll margin value
 * @see [MDN scroll-margin-inline-end](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-margin-inline-end)
 */
@Serializable
data class ScrollMarginInlineEndProperty(
    val margin: IRLength
) : IRProperty {
    override val propertyName = "scroll-margin-inline-end"
}
