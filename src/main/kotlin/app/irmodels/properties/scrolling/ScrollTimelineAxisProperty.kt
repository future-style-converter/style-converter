package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-timeline-axis` property.
 * Specifies the axis for a scroll timeline.
 */
@Serializable
data class ScrollTimelineAxisProperty(
    val axis: ScrollTimelineAxis
) : IRProperty {
    override val propertyName = "scroll-timeline-axis"
}
