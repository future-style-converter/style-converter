package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ScrollSnapStopProperty(
    val stop: SnapStop
) : IRProperty {
    override val propertyName = "scroll-snap-stop"

    enum class SnapStop {
        NORMAL,
        ALWAYS
    }
}
