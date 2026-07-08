package app.irmodels.properties.layout.position

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class PositionProperty(
    val value: Position
) : IRProperty {
    override val propertyName = "position"

    enum class Position {
        STATIC, RELATIVE, ABSOLUTE, FIXED, STICKY
    }
}
