package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-margin-inline-start` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-margin-inline-start: <length>`
 *
 * ## Description
 * Defines the scroll margin at the inline-start edge (left in LTR, right in RTL).
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * ScrollMarginInlineStartProperty(margin = IRLength(10.0, LengthUnit.PX))
 * ```
 *
 * @property margin The inline-start scroll margin value
 * @see [MDN scroll-margin-inline-start](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-margin-inline-start)
 */
@Serializable
data class ScrollMarginInlineStartProperty(
    val margin: IRLength
) : IRProperty {
    override val propertyName = "scroll-margin-inline-start"
}
