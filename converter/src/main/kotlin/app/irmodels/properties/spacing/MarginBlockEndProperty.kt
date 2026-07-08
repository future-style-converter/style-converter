package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `margin-block-end` property.
 *
 * ## CSS Property
 * **Syntax**: `margin-block-end: <length-percentage> | auto`
 *
 * ## Description
 * Defines the logical block end margin (bottom in horizontal-tb writing mode).
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * MarginBlockEndProperty(margin = MarginValue.Length(IRLength(10.0, LengthUnit.PX)))
 * MarginBlockEndProperty(margin = MarginValue.Auto)
 * ```
 *
 * @property margin The block-end margin value
 * @see [MDN margin-block-end](https://developer.mozilla.org/en-US/docs/Web/CSS/margin-block-end)
 */
@Serializable
data class MarginBlockEndProperty(
    val margin: MarginValue
) : IRProperty {
    override val propertyName = "margin-block-end"
}
