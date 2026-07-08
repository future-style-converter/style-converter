package app.irmodels.properties.animations

import app.irmodels.IRProperty
import app.irmodels.IRTime
import kotlinx.serialization.Serializable

@Serializable
data class AnimationDelayProperty(
    val delays: List<IRTime>
) : IRProperty {
    override val propertyName = "animation-delay"
}
