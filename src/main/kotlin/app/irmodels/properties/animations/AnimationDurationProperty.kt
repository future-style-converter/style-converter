package app.irmodels.properties.animations

import app.irmodels.IRProperty
import app.irmodels.IRTime
import kotlinx.serialization.Serializable

@Serializable
data class AnimationDurationProperty(
    val value: AnimationDurationValue
) : IRProperty {
    override val propertyName = "animation-duration"

    @Serializable
    sealed interface AnimationDurationValue {
        @Serializable data class Durations(val durations: List<IRTime>) : AnimationDurationValue
        @Serializable data class Keyword(val keyword: String) : AnimationDurationValue
        @Serializable data class Expression(val expr: String) : AnimationDurationValue
    }
}
