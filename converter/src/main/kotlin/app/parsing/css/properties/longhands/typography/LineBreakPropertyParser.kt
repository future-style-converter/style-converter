package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.LineBreak
import app.irmodels.properties.typography.LineBreakProperty
import app.parsing.css.properties.longhands.PropertyParser

object LineBreakPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val lineBreak = when (trimmed) {
            "auto" -> LineBreak.AUTO
            "loose" -> LineBreak.LOOSE
            "normal" -> LineBreak.NORMAL
            "strict" -> LineBreak.STRICT
            "anywhere" -> LineBreak.ANYWHERE
            else -> return null
        }
        return LineBreakProperty(lineBreak)
    }
}
