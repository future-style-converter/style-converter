package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationFillModeProperty
import app.parsing.css.properties.longhands.PropertyParser

object AnimationFillModePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val fillModes = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            when (part) {
                "none" -> AnimationFillModeProperty.FillMode.NONE
                "forwards" -> AnimationFillModeProperty.FillMode.FORWARDS
                "backwards" -> AnimationFillModeProperty.FillMode.BACKWARDS
                "both" -> AnimationFillModeProperty.FillMode.BOTH
                else -> return null
            }
        }
        if (fillModes.isEmpty()) return null
        return AnimationFillModeProperty(fillModes)
    }
}
