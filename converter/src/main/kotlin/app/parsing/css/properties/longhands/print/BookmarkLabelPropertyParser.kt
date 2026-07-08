package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.BookmarkLabelProperty
import app.irmodels.properties.print.BookmarkLabelValue
import app.parsing.css.properties.longhands.PropertyParser

object BookmarkLabelPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("attr(") && trimmed.endsWith(")") -> {
                val attrName = trimmed.removePrefix("attr(").removeSuffix(")").trim()
                BookmarkLabelProperty(BookmarkLabelValue.Attr(attrName))
            }
            else -> BookmarkLabelProperty(BookmarkLabelValue.Content(trimmed))
        }
    }
}
