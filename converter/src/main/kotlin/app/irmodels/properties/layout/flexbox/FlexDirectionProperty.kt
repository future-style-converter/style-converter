package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FlexDirectionProperty(
    val direction: FlexDirection
) : IRProperty {
    override val propertyName = "flex-direction"

    enum class FlexDirection {
        ROW, ROW_REVERSE, COLUMN, COLUMN_REVERSE
    }
}
