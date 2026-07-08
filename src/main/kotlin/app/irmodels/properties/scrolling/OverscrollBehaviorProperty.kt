package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class OverscrollBehaviorProperty(
    val x: Behavior,
    val y: Behavior?
) : IRProperty {
    override val propertyName = "overscroll-behavior"

    enum class Behavior {
        AUTO,
        CONTAIN,
        NONE
    }
}
