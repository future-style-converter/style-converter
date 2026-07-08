package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextAlignAllProperty
import app.irmodels.properties.typography.TextAlignAllValue
import app.parsing.css.properties.longhands.PropertyParser

object TextAlignAllPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "start" -> TextAlignAllValue.START
            "end" -> TextAlignAllValue.END
            "left" -> TextAlignAllValue.LEFT
            "right" -> TextAlignAllValue.RIGHT
            "center" -> TextAlignAllValue.CENTER
            "justify" -> TextAlignAllValue.JUSTIFY
            "match-parent" -> TextAlignAllValue.MATCH_PARENT
            else -> return null
        }
        return TextAlignAllProperty(v)
    }
}
