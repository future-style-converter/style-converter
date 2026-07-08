package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextGroupAlignProperty
import app.irmodels.properties.typography.TextGroupAlignValue
import app.parsing.css.properties.longhands.PropertyParser

object TextGroupAlignPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val alignValue = when (trimmed) {
            "none" -> TextGroupAlignValue.NONE
            "start" -> TextGroupAlignValue.START
            "end" -> TextGroupAlignValue.END
            "left" -> TextGroupAlignValue.LEFT
            "right" -> TextGroupAlignValue.RIGHT
            "center" -> TextGroupAlignValue.CENTER
            else -> return null
        }

        return TextGroupAlignProperty(alignValue)
    }
}
