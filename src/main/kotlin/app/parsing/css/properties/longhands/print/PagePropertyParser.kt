package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.PageProperty
import app.irmodels.properties.print.PageValue
import app.parsing.css.properties.longhands.PropertyParser

object PagePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        return when (trimmed.lowercase()) {
            "auto" -> PageProperty(PageValue.Auto)
            else -> PageProperty(PageValue.Named(trimmed))
        }
    }
}
