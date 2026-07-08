package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class OverflowAnchorProperty(
    val anchor: OverflowAnchor
) : IRProperty {
    override val propertyName = "overflow-anchor"

    enum class OverflowAnchor {
        AUTO,
        NONE
    }
}
