package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class GridAutoFlowProperty(
    val direction: FlowDirection,
    val dense: Boolean = false
) : IRProperty {
    override val propertyName = "grid-auto-flow"

    enum class FlowDirection {
        ROW, COLUMN
    }
}
