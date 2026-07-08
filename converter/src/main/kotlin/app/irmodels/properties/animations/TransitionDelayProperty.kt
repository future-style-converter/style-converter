package app.irmodels.properties.animations

import app.irmodels.IRProperty
import app.irmodels.IRTime
import kotlinx.serialization.Serializable

@Serializable
data class TransitionDelayProperty(
    val delays: List<IRTime>
) : IRProperty {
    override val propertyName = "transition-delay"
}
