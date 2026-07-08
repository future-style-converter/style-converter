package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.TransitionPropertyProperty
import app.parsing.css.properties.longhands.PropertyParser

object TransitionPropertyPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val properties = trimmed.split(Regex("\\s*,\\s*")).map { part ->
            when (part) {
                "none" -> TransitionPropertyProperty.TransitionProperty.None()
                "all" -> TransitionPropertyProperty.TransitionProperty.All()
                else -> TransitionPropertyProperty.TransitionProperty.PropertyName(part)
            }
        }
        return TransitionPropertyProperty(properties)
    }
}
