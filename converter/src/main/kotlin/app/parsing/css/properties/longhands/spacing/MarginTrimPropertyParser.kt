package app.parsing.css.properties.longhands.spacing

import app.irmodels.IRProperty
import app.irmodels.properties.spacing.MarginTrimProperty
import app.irmodels.properties.spacing.MarginTrimValue
import app.parsing.css.properties.longhands.PropertyParser

object MarginTrimPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val trimValue = when (trimmed) {
            "none" -> MarginTrimValue.NONE
            "block" -> MarginTrimValue.BLOCK
            "block-start" -> MarginTrimValue.BLOCK_START
            "block-end" -> MarginTrimValue.BLOCK_END
            "inline" -> MarginTrimValue.INLINE
            "inline-start" -> MarginTrimValue.INLINE_START
            "inline-end" -> MarginTrimValue.INLINE_END
            else -> return null
        }
        return MarginTrimProperty(trimValue)
    }
}
