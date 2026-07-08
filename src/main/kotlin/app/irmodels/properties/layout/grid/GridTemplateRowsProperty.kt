package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class GridTemplateRowsProperty(
    val value: GridTemplate
) : IRProperty {
    override val propertyName = "grid-template-rows"
}
