package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationNameProperty
import app.parsing.css.properties.longhands.PropertyParser

object AnimationNamePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val names = trimmed.split(Regex("\\s*,\\s*")).map { part ->
            when (part) {
                "none" -> AnimationNameProperty.AnimationName.None()
                else -> AnimationNameProperty.AnimationName.Identifier(part)
            }
        }
        return AnimationNameProperty(names)
    }
}
