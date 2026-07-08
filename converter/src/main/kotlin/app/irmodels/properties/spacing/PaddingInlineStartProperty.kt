package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `padding-inline-start` property.
 *
 * ## CSS Property
 * **Syntax**: `padding-inline-start: <length-percentage>`
 *
 * ## Description
 * Defines the logical inline start padding (left in LTR, right in RTL).
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * PaddingInlineStartProperty(padding = PaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ```
 *
 * @property padding The inline-start padding value
 * @see [MDN padding-inline-start](https://developer.mozilla.org/en-US/docs/Web/CSS/padding-inline-start)
 */
@Serializable
data class PaddingInlineStartProperty(
    val padding: PaddingValue
) : IRProperty {
    override val propertyName = "padding-inline-start"
}
