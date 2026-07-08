package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `view-timeline-axis` property.
 * Specifies the axis for a view timeline.
 */
@Serializable
data class ViewTimelineAxisProperty(
    val axis: ViewTimelineAxis
) : IRProperty {
    override val propertyName = "view-timeline-axis"
}
