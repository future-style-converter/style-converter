package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.LineStyle
import app.irmodels.properties.borders.BorderStyleProperty
import app.parsing.css.properties.longhands.PropertyParser

object BorderStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val style = when (trimmed) {
            "none" -> LineStyle.NONE
            "hidden" -> LineStyle.HIDDEN
            "dotted" -> LineStyle.DOTTED
            "dashed" -> LineStyle.DASHED
            "solid" -> LineStyle.SOLID
            "double" -> LineStyle.DOUBLE
            "groove" -> LineStyle.GROOVE
            "ridge" -> LineStyle.RIDGE
            "inset" -> LineStyle.INSET
            "outset" -> LineStyle.OUTSET
            else -> return null
        }
        return BorderStyleProperty(style)
    }
}
