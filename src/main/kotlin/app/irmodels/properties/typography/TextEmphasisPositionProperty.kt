package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextEmphasisVertical {
    OVER,
    UNDER
}

enum class TextEmphasisHorizontal {
    LEFT,
    RIGHT
}

/**
 * Represents the CSS `text-emphasis-position` property.
 * Sets position of emphasis marks.
 */
@Serializable
data class TextEmphasisPositionProperty(
    val vertical: TextEmphasisVertical,
    val horizontal: TextEmphasisHorizontal? = null
) : IRProperty {
    override val propertyName = "text-emphasis-position"
}
