package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `margin-inline-start` property.
 *
 * ## CSS Property
 * **Syntax**: `margin-inline-start: <length-percentage> | auto`
 *
 * ## Description
 * Defines the logical inline start margin (left in LTR, right in RTL).
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * MarginInlineStartProperty(margin = MarginValue.Length(IRLength(10.0, LengthUnit.PX)))
 * MarginInlineStartProperty(margin = MarginValue.Auto)
 * ```
 *
 * @property margin The inline-start margin value
 * @see [MDN margin-inline-start](https://developer.mozilla.org/en-US/docs/Web/CSS/margin-inline-start)
 */
@Serializable
data class MarginInlineStartProperty(
    val margin: MarginValue
) : IRProperty {
    override val propertyName = "margin-inline-start"
}
