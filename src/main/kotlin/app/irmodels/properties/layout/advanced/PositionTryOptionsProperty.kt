package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class PositionTryOption {
    FLIP_BLOCK,
    FLIP_INLINE,
    FLIP_START
}

/**
 * Represents the CSS `position-try-options` property (deprecated).
 * Options for position fallback strategies.
 */
@Serializable
data class PositionTryOptionsProperty(
    val options: List<PositionTryOption>
) : IRProperty {
    override val propertyName = "position-try-options"
}
