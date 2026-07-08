package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextAlignProperty
import app.parsing.css.properties.longhands.PropertyParser

object TextAlignPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val alignment = when (trimmed) {
            "start" -> TextAlignProperty.TextAlignment.START
            "end" -> TextAlignProperty.TextAlignment.END
            "left" -> TextAlignProperty.TextAlignment.LEFT
            "right" -> TextAlignProperty.TextAlignment.RIGHT
            "center" -> TextAlignProperty.TextAlignment.CENTER
            "justify" -> TextAlignProperty.TextAlignment.JUSTIFY
            "match-parent" -> TextAlignProperty.TextAlignment.MATCH_PARENT
            else -> return null
        }
        return TextAlignProperty(alignment)
    }
}
