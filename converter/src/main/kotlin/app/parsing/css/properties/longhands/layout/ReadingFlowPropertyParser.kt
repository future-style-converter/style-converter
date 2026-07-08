package app.parsing.css.properties.longhands.layout

import app.irmodels.IRProperty
import app.irmodels.properties.layout.ReadingFlowProperty
import app.irmodels.properties.layout.ReadingFlowValue
import app.parsing.css.properties.longhands.PropertyParser

object ReadingFlowPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "normal" -> ReadingFlowValue.NORMAL
            "flex-visual" -> ReadingFlowValue.FLEX_VISUAL
            "flex-flow" -> ReadingFlowValue.FLEX_FLOW
            "grid-rows" -> ReadingFlowValue.GRID_ROWS
            "grid-columns" -> ReadingFlowValue.GRID_COLUMNS
            "grid-order" -> ReadingFlowValue.GRID_ORDER
            else -> return null
        }
        return ReadingFlowProperty(v)
    }
}
