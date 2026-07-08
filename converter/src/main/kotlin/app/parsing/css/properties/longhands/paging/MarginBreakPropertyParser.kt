package app.parsing.css.properties.longhands.paging

import app.irmodels.IRProperty
import app.irmodels.properties.paging.MarginBreakProperty
import app.irmodels.properties.paging.MarginBreakValue
import app.parsing.css.properties.longhands.PropertyParser

object MarginBreakPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "auto" -> MarginBreakValue.AUTO
            "keep" -> MarginBreakValue.KEEP
            "discard" -> MarginBreakValue.DISCARD
            else -> return null
        }
        return MarginBreakProperty(v)
    }
}
