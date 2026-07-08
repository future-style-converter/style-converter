package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationPlayStateProperty
import app.parsing.css.properties.longhands.PropertyParser

object AnimationPlayStatePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val states = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            when (part) {
                "running" -> AnimationPlayStateProperty.PlayState.RUNNING
                "paused" -> AnimationPlayStateProperty.PlayState.PAUSED
                else -> return null
            }
        }
        if (states.isEmpty()) return null
        return AnimationPlayStateProperty(states)
    }
}
