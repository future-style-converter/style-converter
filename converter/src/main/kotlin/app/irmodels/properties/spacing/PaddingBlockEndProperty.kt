package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `padding-block-end` property.
 *
 * ## CSS Property
 * **Syntax**: `padding-block-end: <length-percentage>`
 *
 * ## Description
 * Defines the logical block end padding (bottom in horizontal-tb writing mode).
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * PaddingBlockEndProperty(padding = PaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ```
 *
 * @property padding The block-end padding value
 * @see [MDN padding-block-end](https://developer.mozilla.org/en-US/docs/Web/CSS/padding-block-end)
 */
@Serializable
data class PaddingBlockEndProperty(
    val padding: PaddingValue
) : IRProperty {
    override val propertyName = "padding-block-end"
}
