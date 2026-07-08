package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.FontStyleProperty
import app.parsing.css.properties.longhands.PropertyParser

object FontStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val style = when {
            trimmed == "normal" -> FontStyleProperty.FontStyle.Normal()
            trimmed == "italic" -> FontStyleProperty.FontStyle.Italic()
            trimmed == "oblique" -> FontStyleProperty.FontStyle.Oblique()
            trimmed.startsWith("oblique ") -> {
                // Parse angle: oblique 14deg
                val anglePart = trimmed.substring(8).trim()
                val angle = parseAngle(anglePart)
                FontStyleProperty.FontStyle.Oblique(angle)
            }
            else -> return null
        }

        return FontStyleProperty(style)
    }

    private fun parseAngle(value: String): IRAngle? {
        if (value.endsWith("deg")) {
            val num = value.removeSuffix("deg").toDoubleOrNull() ?: return null
            return IRAngle.fromDegrees(num)
        }
        if (value.endsWith("rad")) {
            val num = value.removeSuffix("rad").toDoubleOrNull() ?: return null
            return IRAngle.fromRadians(num)
        }
        if (value.endsWith("grad")) {
            val num = value.removeSuffix("grad").toDoubleOrNull() ?: return null
            return IRAngle.fromGradians(num)
        }
        if (value.endsWith("turn")) {
            val num = value.removeSuffix("turn").toDoubleOrNull() ?: return null
            return IRAngle.fromTurns(num)
        }
        return null
    }
}
