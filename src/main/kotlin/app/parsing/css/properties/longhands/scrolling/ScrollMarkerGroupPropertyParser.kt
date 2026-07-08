package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollMarkerGroupProperty
import app.irmodels.properties.scrolling.ScrollMarkerGroupValue
import app.parsing.css.properties.longhands.PropertyParser

object ScrollMarkerGroupPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "none" -> ScrollMarkerGroupValue.NONE
            "before" -> ScrollMarkerGroupValue.BEFORE
            "after" -> ScrollMarkerGroupValue.AFTER
            else -> return null
        }
        return ScrollMarkerGroupProperty(v)
    }
}
