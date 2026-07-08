package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `view-timeline-name` property.
 * Names a view timeline for scroll-driven animations.
 */
@Serializable
data class ViewTimelineNameProperty(
    val name: String
) : IRProperty {
    override val propertyName = "view-timeline-name"
}
