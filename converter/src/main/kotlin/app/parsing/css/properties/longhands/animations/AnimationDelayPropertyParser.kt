package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationDelayProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.TimeParser

object AnimationDelayPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val delays = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            TimeParser.parse(part)
        }
        if (delays.isEmpty()) return null
        return AnimationDelayProperty(delays)
    }
}
