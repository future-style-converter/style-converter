package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ScrollTimelineName(val name: String)

enum class ScrollTimelineAxis {
    BLOCK,
    INLINE,
    X,
    Y
}

/**
 * Represents the CSS `scroll-timeline` shorthand property.
 * Defines a scroll-driven animation timeline.
 */
@Serializable
data class ScrollTimelineProperty(
    val name: ScrollTimelineName,
    val axis: ScrollTimelineAxis
) : IRProperty {
    override val propertyName = "scroll-timeline"
}
