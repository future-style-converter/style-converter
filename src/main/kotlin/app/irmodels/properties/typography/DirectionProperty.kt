package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class DirectionProperty(
    val direction: Direction
) : IRProperty {
    override val propertyName = "direction"

    enum class Direction {
        LTR, RTL
    }
}
