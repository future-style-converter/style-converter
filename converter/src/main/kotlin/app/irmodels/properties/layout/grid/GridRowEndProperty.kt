package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class GridRowEndProperty(
    val value: GridLine
) : IRProperty {
    override val propertyName = "grid-row-end"
}
