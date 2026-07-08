package app.irmodels.properties.interactions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TouchActionProperty(
    val values: List<TouchAction>
) : IRProperty {
    override val propertyName = "touch-action"

    enum class TouchAction {
        AUTO, NONE, PAN_X, PAN_LEFT, PAN_RIGHT, PAN_Y,
        PAN_UP, PAN_DOWN, PINCH_ZOOM, MANIPULATION
    }
}
