package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollStartTargetXProperty
import app.irmodels.properties.scrolling.ScrollStartTargetValue
import app.parsing.css.properties.longhands.PropertyParser

object ScrollStartTargetXPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "none" -> ScrollStartTargetValue.NONE
            "auto" -> ScrollStartTargetValue.AUTO
            else -> return null
        }
        return ScrollStartTargetXProperty(v)
    }
}
