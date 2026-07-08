package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GridTemplateAreasProperty(
    val value: GridTemplateAreas
) : IRProperty {
    override val propertyName = "grid-template-areas"

    @Serializable
    sealed interface GridTemplateAreas {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : GridTemplateAreas

        @Serializable
        @SerialName("areas")
        data class Areas(val rows: List<List<String>>) : GridTemplateAreas
    }
}
