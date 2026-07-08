package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollTimelineName
import app.irmodels.properties.scrolling.ScrollTimelineNameProperty
import app.parsing.css.properties.longhands.PropertyParser

object ScrollTimelineNamePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        return ScrollTimelineNameProperty(ScrollTimelineName(trimmed))
    }
}
