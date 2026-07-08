package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class PositionTryOrderValue {
    NORMAL,
    MOST_WIDTH,
    MOST_HEIGHT,
    MOST_BLOCK_SIZE,
    MOST_INLINE_SIZE
}

/**
 * Represents the CSS `position-try-order` property.
 * Specifies the order in which position fallbacks are tried.
 */
@Serializable
data class PositionTryOrderProperty(
    val value: PositionTryOrderValue
) : IRProperty {
    override val propertyName = "position-try-order"
}
