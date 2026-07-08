package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ViewTimelineAxis {
    BLOCK,
    INLINE,
    X,
    Y
}

/**
 * Represents the CSS `view-timeline` shorthand property.
 * Defines a view progress timeline for scroll-driven animations.
 */
@Serializable
data class ViewTimelineProperty(
    val name: String? = null,
    val axis: ViewTimelineAxis = ViewTimelineAxis.BLOCK
) : IRProperty {
    override val propertyName = "view-timeline"
}
