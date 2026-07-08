package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/** CSS `grid-template-columns` property. */
@Serializable
data class GridTemplateColumnsProperty(val value: GridTemplate) : IRProperty {
    override val propertyName = "grid-template-columns"
}
