package app.irmodels.properties.animations

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ViewTimelineInsetValue {
    @Serializable
    @SerialName("auto")
    data object Auto : ViewTimelineInsetValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : ViewTimelineInsetValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : ViewTimelineInsetValue
}

/**
 * Represents the CSS `view-timeline-inset` property.
 * Adjusts the view timeline range with insets.
 */
@Serializable
data class ViewTimelineInsetProperty(
    val start: ViewTimelineInsetValue,
    val end: ViewTimelineInsetValue
) : IRProperty {
    override val propertyName = "view-timeline-inset"
}
