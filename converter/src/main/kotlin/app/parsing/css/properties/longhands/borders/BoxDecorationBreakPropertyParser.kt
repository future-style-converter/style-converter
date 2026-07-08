package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.BoxDecorationBreakProperty
import app.irmodels.properties.borders.BoxDecorationBreak
import app.parsing.css.properties.longhands.PropertyParser

object BoxDecorationBreakPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val breakValue = when (trimmed) {
            "slice" -> BoxDecorationBreak.SLICE
            "clone" -> BoxDecorationBreak.CLONE
            else -> return null
        }
        return BoxDecorationBreakProperty(breakValue)
    }
}
