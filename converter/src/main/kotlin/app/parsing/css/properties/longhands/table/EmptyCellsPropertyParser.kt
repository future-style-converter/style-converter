package app.parsing.css.properties.longhands.table

import app.irmodels.IRProperty
import app.irmodels.properties.table.EmptyCellsProperty
import app.parsing.css.properties.longhands.PropertyParser

object EmptyCellsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val cells = when (value.trim().lowercase()) {
            "show" -> EmptyCellsProperty.EmptyCells.SHOW
            "hide" -> EmptyCellsProperty.EmptyCells.HIDE
            else -> return null
        }
        return EmptyCellsProperty(cells)
    }
}
