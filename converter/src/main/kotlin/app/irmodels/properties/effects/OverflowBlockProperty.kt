package app.irmodels.properties.effects

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `overflow-block` property.
 *
 * ## CSS Property
 * **Syntax**: `overflow-block: visible | hidden | clip | scroll | auto`
 *
 * ## Description
 * Controls what happens when content overflows in the block direction (vertical in
 * horizontal-tb writing mode). This is a logical property equivalent of overflow-y.
 *
 * ## Examples
 * ```kotlin
 * OverflowBlockProperty(overflow = OverflowValue.Auto)
 * OverflowBlockProperty(overflow = OverflowValue.Hidden)
 * OverflowBlockProperty(overflow = OverflowValue.Scroll)
 * ```
 *
 * @property overflow The overflow behavior value
 * @see [MDN overflow-block](https://developer.mozilla.org/en-US/docs/Web/CSS/overflow-block)
 */
@Serializable
data class OverflowBlockProperty(
    val overflow: OverflowValue
) : IRProperty {
    override val propertyName = "overflow-block"
}
