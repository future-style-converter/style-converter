package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollStartTargetProperty
import app.irmodels.properties.scrolling.ScrollStartTargetValue
import app.parsing.css.properties.longhands.PropertyParser

object ScrollStartTargetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val targetValue = when (trimmed) {
            "none" -> ScrollStartTargetValue.NONE
            "auto" -> ScrollStartTargetValue.AUTO
            else -> return null
        }
        return ScrollStartTargetProperty(targetValue)
    }
}
