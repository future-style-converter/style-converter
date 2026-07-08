package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class AnimationFillModeProperty(
    val fillModes: List<FillMode>
) : IRProperty {
    override val propertyName = "animation-fill-mode"

    enum class FillMode {
        NONE, FORWARDS, BACKWARDS, BOTH
    }
}
