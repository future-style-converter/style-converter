package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `padding-inline-end` property.
 *
 * ## CSS Property
 * **Syntax**: `padding-inline-end: <length-percentage>`
 *
 * ## Description
 * Defines the logical inline end padding (right in LTR, left in RTL).
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * PaddingInlineEndProperty(padding = PaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ```
 *
 * @property padding The inline-end padding value
 * @see [MDN padding-inline-end](https://developer.mozilla.org/en-US/docs/Web/CSS/padding-inline-end)
 */
@Serializable
data class PaddingInlineEndProperty(
    val padding: PaddingValue
) : IRProperty {
    override val propertyName = "padding-inline-end"
}
