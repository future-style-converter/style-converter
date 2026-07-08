package app.irmodels.properties.layout.position

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `inset-block-start` property.
 *
 * ## CSS Property
 * **Syntax**: `inset-block-start: <length-percentage> | auto`
 *
 * ## Description
 * Defines the logical block start offset (top in horizontal-tb writing mode) of an element.
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * InsetBlockStartProperty(value = InsetValue.Length(IRLength(10.0, LengthUnit.PX)))
 * InsetBlockStartProperty(value = InsetValue.Percentage(IRPercentage(50.0)))
 * InsetBlockStartProperty(value = InsetValue.Auto)
 * ```
 *
 * @property value The block-start inset value
 * @see [MDN inset-block-start](https://developer.mozilla.org/en-US/docs/Web/CSS/inset-block-start)
 */
@Serializable
data class InsetBlockStartProperty(
    val value: InsetValue
) : IRProperty {
    override val propertyName = "inset-block-start"
}
