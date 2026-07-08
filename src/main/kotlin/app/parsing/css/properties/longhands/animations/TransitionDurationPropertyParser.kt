package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.TransitionDurationProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.TimeParser

object TransitionDurationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val durations = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            TimeParser.parse(part)
        }
        if (durations.isEmpty()) return null
        return TransitionDurationProperty(durations)
    }
}
