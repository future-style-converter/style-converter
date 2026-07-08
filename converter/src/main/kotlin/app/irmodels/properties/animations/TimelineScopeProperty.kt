package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TimelineScopeValue {
    @Serializable
    @SerialName("none")
    data object None : TimelineScopeValue

    @Serializable
    @SerialName("all")
    data object All : TimelineScopeValue

    @Serializable
    @SerialName("names")
    data class Names(val names: List<String>) : TimelineScopeValue
}

/**
 * Represents the CSS `timeline-scope` property.
 * Modifies the scope of named timelines.
 */
@Serializable
data class TimelineScopeProperty(
    val value: TimelineScopeValue
) : IRProperty {
    override val propertyName = "timeline-scope"
}
