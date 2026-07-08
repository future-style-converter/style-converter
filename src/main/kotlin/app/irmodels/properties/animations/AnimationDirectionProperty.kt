package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class AnimationDirectionProperty(
    val directions: List<Direction>
) : IRProperty {
    override val propertyName = "animation-direction"

    enum class Direction {
        NORMAL, REVERSE, ALTERNATE, ALTERNATE_REVERSE
    }
}
