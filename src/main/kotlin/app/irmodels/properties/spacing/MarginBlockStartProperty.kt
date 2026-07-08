package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `margin-block-start` property.
 *
 * ## CSS Property
 * **Syntax**: `margin-block-start: <length-percentage> | auto`
 *
 * ## Description
 * Defines the logical block start margin (top in horizontal-tb writing mode).
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * MarginBlockStartProperty(margin = MarginValue.Length(IRLength(10.0, LengthUnit.PX)))
 * MarginBlockStartProperty(margin = MarginValue.Auto)
 * ```
 *
 * @property margin The block-start margin value
 * @see [MDN margin-block-start](https://developer.mozilla.org/en-US/docs/Web/CSS/margin-block-start)
 */
@Serializable
data class MarginBlockStartProperty(
    val margin: MarginValue
) : IRProperty {
    override val propertyName = "margin-block-start"
}
