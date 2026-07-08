package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontDisplayProperty
import app.irmodels.properties.typography.FontDisplayValue
import app.parsing.css.properties.longhands.PropertyParser

object FontDisplayPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val displayValue = when (trimmed) {
            "auto" -> FontDisplayValue.AUTO
            "block" -> FontDisplayValue.BLOCK
            "swap" -> FontDisplayValue.SWAP
            "fallback" -> FontDisplayValue.FALLBACK
            "optional" -> FontDisplayValue.OPTIONAL
            else -> return null
        }

        return FontDisplayProperty(displayValue)
    }
}
