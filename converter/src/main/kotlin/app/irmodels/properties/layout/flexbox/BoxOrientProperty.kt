package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `-webkit-box-orient` property.
 * This is a legacy flexbox property used for multi-line text truncation.
 */
@Serializable
data class BoxOrientProperty(
    val value: BoxOrient
) : IRProperty {
    override val propertyName = "-webkit-box-orient"

    @Serializable
    enum class BoxOrient {
        HORIZONTAL, VERTICAL, INLINE_AXIS, BLOCK_AXIS
    }
}
