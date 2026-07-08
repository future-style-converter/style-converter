package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollTargetGroupProperty
import app.irmodels.properties.scrolling.ScrollTargetGroupValue
import app.parsing.css.properties.longhands.PropertyParser

object ScrollTargetGroupPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val v = when (trimmed.lowercase()) {
            "none" -> ScrollTargetGroupValue.None
            else -> ScrollTargetGroupValue.Named(trimmed)
        }
        return ScrollTargetGroupProperty(v)
    }
}
