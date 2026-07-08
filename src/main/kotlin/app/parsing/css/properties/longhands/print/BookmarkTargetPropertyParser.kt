package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.BookmarkTargetProperty
import app.irmodels.properties.print.BookmarkTargetValue
import app.parsing.css.properties.longhands.PropertyParser

object BookmarkTargetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        return when {
            trimmed.lowercase() == "self" -> BookmarkTargetProperty(BookmarkTargetValue.Self)
            trimmed.startsWith("url(") && trimmed.endsWith(")") -> {
                val url = trimmed.removePrefix("url(").removeSuffix(")").trim()
                    .removeSurrounding("\"").removeSurrounding("'")
                BookmarkTargetProperty(BookmarkTargetValue.Url(url))
            }
            trimmed.startsWith("attr(") && trimmed.endsWith(")") -> {
                val attr = trimmed.removePrefix("attr(").removeSuffix(")").trim()
                BookmarkTargetProperty(BookmarkTargetValue.Attr(attr))
            }
            else -> null
        }
    }
}
