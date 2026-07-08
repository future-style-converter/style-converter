package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollStartTargetInlineProperty
import app.irmodels.properties.scrolling.ScrollStartTargetValue
import app.parsing.css.properties.longhands.PropertyParser

object ScrollStartTargetInlinePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "none" -> ScrollStartTargetValue.NONE
            "auto" -> ScrollStartTargetValue.AUTO
            else -> return null
        }
        return ScrollStartTargetInlineProperty(v)
    }
}
