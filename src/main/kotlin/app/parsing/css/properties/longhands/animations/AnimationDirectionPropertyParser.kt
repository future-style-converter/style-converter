package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationDirectionProperty
import app.parsing.css.properties.longhands.PropertyParser

object AnimationDirectionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val directions = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            when (part) {
                "normal" -> AnimationDirectionProperty.Direction.NORMAL
                "reverse" -> AnimationDirectionProperty.Direction.REVERSE
                "alternate" -> AnimationDirectionProperty.Direction.ALTERNATE
                "alternate-reverse" -> AnimationDirectionProperty.Direction.ALTERNATE_REVERSE
                else -> return null
            }
        }
        if (directions.isEmpty()) return null
        return AnimationDirectionProperty(directions)
    }
}
