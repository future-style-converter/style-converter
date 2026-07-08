package app.parsing.css.properties.longhands.print

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.print.BookmarkLevelProperty
import app.irmodels.properties.print.BookmarkLevelValue
import app.parsing.css.properties.longhands.PropertyParser

object BookmarkLevelPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        return when (trimmed) {
            "none" -> BookmarkLevelProperty(BookmarkLevelValue.None)
            else -> {
                val num = trimmed.toIntOrNull() ?: return null
                if (num < 1) return null
                BookmarkLevelProperty(BookmarkLevelValue.Integer(IRNumber(num.toDouble())))
            }
        }
    }
}
