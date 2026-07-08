package app.irmodels.properties.layout.position

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `inset-inline-end` property.
 *
 * ## CSS Property
 * **Syntax**: `inset-inline-end: <length-percentage> | auto`
 *
 * ## Description
 * Defines the logical inline end offset (right in LTR, left in RTL) of an element.
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * InsetInlineEndProperty(value = InsetValue.Length(IRLength(10.0, LengthUnit.PX)))
 * InsetInlineEndProperty(value = InsetValue.Percentage(IRPercentage(50.0)))
 * InsetInlineEndProperty(value = InsetValue.Auto)
 * ```
 *
 * @property value The inline-end inset value
 * @see [MDN inset-inline-end](https://developer.mozilla.org/en-US/docs/Web/CSS/inset-inline-end)
 */
@Serializable
data class InsetInlineEndProperty(
    val value: InsetValue
) : IRProperty {
    override val propertyName = "inset-inline-end"
}
