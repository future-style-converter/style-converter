package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class AnimationPlayStateProperty(
    val states: List<PlayState>
) : IRProperty {
    override val propertyName = "animation-play-state"

    enum class PlayState {
        RUNNING, PAUSED
    }
}
