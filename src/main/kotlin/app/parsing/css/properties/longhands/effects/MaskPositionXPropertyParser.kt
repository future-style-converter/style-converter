package app.parsing.css.properties.longhands.effects

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.effects.MaskPositionValue
import app.irmodels.properties.effects.MaskPositionXProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object MaskPositionXPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val posValue = when (trimmed) {
            "center" -> MaskPositionValue.Center
            "left" -> MaskPositionValue.Left
            "right" -> MaskPositionValue.Right
            else -> {
                if (trimmed.endsWith("%")) {
                    val percent = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
                    MaskPositionValue.Percentage(IRPercentage(percent))
                } else {
                    val length = LengthParser.parse(trimmed) ?: return null
                    MaskPositionValue.Length(length)
                }
            }
        }
        return MaskPositionXProperty(posValue)
    }
}
