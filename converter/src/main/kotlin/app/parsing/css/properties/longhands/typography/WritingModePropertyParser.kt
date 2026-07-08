package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.WritingModeProperty
import app.parsing.css.properties.longhands.PropertyParser

object WritingModePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val mode = when (trimmed) {
            "horizontal-tb" -> WritingModeProperty.WritingMode.HORIZONTAL_TB
            "vertical-rl" -> WritingModeProperty.WritingMode.VERTICAL_RL
            "vertical-lr" -> WritingModeProperty.WritingMode.VERTICAL_LR
            "sideways-rl" -> WritingModeProperty.WritingMode.SIDEWAYS_RL
            "sideways-lr" -> WritingModeProperty.WritingMode.SIDEWAYS_LR
            else -> return null
        }
        return WritingModeProperty(mode)
    }
}
