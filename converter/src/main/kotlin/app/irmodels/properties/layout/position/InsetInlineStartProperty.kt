package app.irmodels.properties.layout.position

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `inset-inline-start` property.
 *
 * ## CSS Property
 * **Syntax**: `inset-inline-start: <length-percentage> | auto`
 *
 * ## Description
 * Defines the logical inline start offset (left in LTR, right in RTL) of an element.
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * InsetInlineStartProperty(value = InsetValue.Length(IRLength(10.0, LengthUnit.PX)))
 * InsetInlineStartProperty(value = InsetValue.Percentage(IRPercentage(50.0)))
 * InsetInlineStartProperty(value = InsetValue.Auto)
 * ```
 *
 * @property value The inline-start inset value
 * @see [MDN inset-inline-start](https://developer.mozilla.org/en-US/docs/Web/CSS/inset-inline-start)
 */
@Serializable
data class InsetInlineStartProperty(
    val value: InsetValue
) : IRProperty {
    override val propertyName = "inset-inline-start"
}
