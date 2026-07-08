package app.irmodels.properties.layout.position

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `inset-block-end` property.
 *
 * ## CSS Property
 * **Syntax**: `inset-block-end: <length-percentage> | auto`
 *
 * ## Description
 * Defines the logical block end offset (bottom in horizontal-tb writing mode) of an element.
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * InsetBlockEndProperty(value = InsetValue.Length(IRLength(10.0, LengthUnit.PX)))
 * InsetBlockEndProperty(value = InsetValue.Percentage(IRPercentage(50.0)))
 * InsetBlockEndProperty(value = InsetValue.Auto)
 * ```
 *
 * @property value The block-end inset value
 * @see [MDN inset-block-end](https://developer.mozilla.org/en-US/docs/Web/CSS/inset-block-end)
 */
@Serializable
data class InsetBlockEndProperty(
    val value: InsetValue
) : IRProperty {
    override val propertyName = "inset-block-end"
}
