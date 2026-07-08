package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-timeline-name` property.
 * Names a scroll timeline for use with scroll-driven animations.
 */
@Serializable
data class ScrollTimelineNameProperty(
    val name: ScrollTimelineName
) : IRProperty {
    override val propertyName = "scroll-timeline-name"
}
