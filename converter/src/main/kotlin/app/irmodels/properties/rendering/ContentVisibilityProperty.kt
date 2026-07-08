package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ContentVisibilityValue {
    VISIBLE,
    AUTO,
    HIDDEN
}

/**
 * Represents the CSS `content-visibility` property.
 * Controls whether content is rendered.
 */
@Serializable
data class ContentVisibilityProperty(
    val value: ContentVisibilityValue
) : IRProperty {
    override val propertyName = "content-visibility"
}
