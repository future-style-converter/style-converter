package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.FootnoteDisplayProperty
import app.irmodels.properties.print.FootnoteDisplayValue
import app.parsing.css.properties.longhands.PropertyParser

object FootnoteDisplayPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val displayValue = when (trimmed) {
            "block" -> FootnoteDisplayValue.BLOCK
            "inline" -> FootnoteDisplayValue.INLINE
            "compact" -> FootnoteDisplayValue.COMPACT
            else -> return null
        }
        return FootnoteDisplayProperty(displayValue)
    }
}
