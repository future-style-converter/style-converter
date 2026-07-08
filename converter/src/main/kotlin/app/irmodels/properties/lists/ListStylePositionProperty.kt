package app.irmodels.properties.lists

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ListStylePositionProperty(
    val position: ListPosition
) : IRProperty {
    override val propertyName = "list-style-position"

    enum class ListPosition {
        INSIDE, OUTSIDE
    }
}
