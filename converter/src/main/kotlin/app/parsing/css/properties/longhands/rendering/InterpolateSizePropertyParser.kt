package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.InterpolateSizeProperty
import app.irmodels.properties.rendering.InterpolateSizeValue
import app.parsing.css.properties.longhands.PropertyParser

object InterpolateSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "numeric-only" -> InterpolateSizeValue.NUMERIC_ONLY
            "allow-keywords" -> InterpolateSizeValue.ALLOW_KEYWORDS
            else -> return null
        }
        return InterpolateSizeProperty(v)
    }
}
