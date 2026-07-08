package app.parsing.css.properties.longhands.columns

import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnSpan
import app.irmodels.properties.columns.ColumnSpanProperty
import app.parsing.css.properties.longhands.PropertyParser

object ColumnSpanPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val span = when (value.trim().lowercase()) {
            "none" -> ColumnSpan.NONE
            "all" -> ColumnSpan.ALL
            else -> return null
        }
        return ColumnSpanProperty(span)
    }
}
