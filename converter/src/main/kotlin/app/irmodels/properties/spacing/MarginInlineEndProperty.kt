package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `margin-inline-end` property.
 *
 * ## CSS Property
 * **Syntax**: `margin-inline-end: <length-percentage> | auto`
 *
 * ## Description
 * Defines the logical inline end margin (right in LTR, left in RTL).
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * MarginInlineEndProperty(margin = MarginValue.Length(IRLength(10.0, LengthUnit.PX)))
 * MarginInlineEndProperty(margin = MarginValue.Auto)
 * ```
 *
 * @property margin The inline-end margin value
 * @see [MDN margin-inline-end](https://developer.mozilla.org/en-US/docs/Web/CSS/margin-inline-end)
 */
@Serializable
data class MarginInlineEndProperty(
    val margin: MarginValue
) : IRProperty {
    override val propertyName = "margin-inline-end"
}
