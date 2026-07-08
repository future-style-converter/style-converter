package app.irmodels.properties.effects

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `overflow-inline` property.
 *
 * ## CSS Property
 * **Syntax**: `overflow-inline: visible | hidden | clip | scroll | auto`
 *
 * ## Description
 * Controls what happens when content overflows in the inline direction (horizontal in
 * horizontal-tb writing mode). This is a logical property equivalent of overflow-x.
 *
 * ## Examples
 * ```kotlin
 * OverflowInlineProperty(overflow = OverflowValue.Auto)
 * OverflowInlineProperty(overflow = OverflowValue.Hidden)
 * OverflowInlineProperty(overflow = OverflowValue.Scroll)
 * ```
 *
 * @property overflow The overflow behavior value
 * @see [MDN overflow-inline](https://developer.mozilla.org/en-US/docs/Web/CSS/overflow-inline)
 */
@Serializable
data class OverflowInlineProperty(
    val overflow: OverflowValue
) : IRProperty {
    override val propertyName = "overflow-inline"
}
