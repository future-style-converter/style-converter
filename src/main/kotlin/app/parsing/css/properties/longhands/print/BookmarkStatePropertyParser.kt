package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.BookmarkStateProperty
import app.irmodels.properties.print.BookmarkStateValue
import app.parsing.css.properties.longhands.PropertyParser

object BookmarkStatePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val stateValue = when (trimmed) {
            "open" -> BookmarkStateValue.OPEN
            "closed" -> BookmarkStateValue.CLOSED
            else -> return null
        }
        return BookmarkStateProperty(stateValue)
    }
}
