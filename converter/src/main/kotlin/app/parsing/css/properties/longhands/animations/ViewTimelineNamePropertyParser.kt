package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.ViewTimelineNameProperty
import app.parsing.css.properties.longhands.PropertyParser

object ViewTimelineNamePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        if (trimmed.lowercase() == "none") {
            return ViewTimelineNameProperty("none")
        }
        return ViewTimelineNameProperty(trimmed)
    }
}
