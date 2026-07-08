package app.irmodels.properties.table

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TableLayoutProperty(
    val layout: TableLayout
) : IRProperty {
    override val propertyName = "table-layout"

    enum class TableLayout {
        AUTO, FIXED
    }
}
