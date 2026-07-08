package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `padding-block-start` property.
 *
 * ## CSS Property
 * **Syntax**: `padding-block-start: <length-percentage>`
 *
 * ## Description
 * Defines the logical block start padding (top in horizontal-tb writing mode).
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * PaddingBlockStartProperty(padding = PaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ```
 *
 * @property padding The block-start padding value
 * @see [MDN padding-block-start](https://developer.mozilla.org/en-US/docs/Web/CSS/padding-block-start)
 */
@Serializable
data class PaddingBlockStartProperty(
    val padding: PaddingValue
) : IRProperty {
    override val propertyName = "padding-block-start"
}
