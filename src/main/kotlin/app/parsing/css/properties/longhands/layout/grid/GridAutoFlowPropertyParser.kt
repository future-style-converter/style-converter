package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.GridAutoFlowProperty
import app.parsing.css.properties.longhands.PropertyParser
object GridAutoFlowPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val parts = trimmed.split(Regex("\\s+"))
        
        var direction = GridAutoFlowProperty.FlowDirection.ROW
        var dense = false
        for (part in parts) {
            when (part) {
                "row" -> direction = GridAutoFlowProperty.FlowDirection.ROW
                "column" -> direction = GridAutoFlowProperty.FlowDirection.COLUMN
                "dense" -> dense = true
                else -> return null
            }
        }
        return GridAutoFlowProperty(direction, dense)
    }
}
