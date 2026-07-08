package app.parsing.css.properties.longhands.speech

import app.irmodels.IRProperty
import app.irmodels.properties.speech.AzimuthProperty
import app.irmodels.properties.speech.AzimuthValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object AzimuthPropertyParser : PropertyParser {
    private val namedPositions = setOf(
        "left-side", "far-left", "left", "center-left", "center",
        "center-right", "right", "far-right", "right-side", "behind",
        "leftwards", "rightwards"
    )

    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return AzimuthProperty(AzimuthValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return AzimuthProperty(AzimuthValue.Raw(trimmed))
        }

        // Check for combined values (e.g., "far-right behind", "left behind")
        val parts = lower.split(Regex("\\s+"))
        if (parts.size == 2) {
            val hasBehind = parts.contains("behind")
            val position = parts.find { it != "behind" }
            if (hasBehind && position != null && position in namedPositions) {
                return AzimuthProperty(AzimuthValue.Combined(position, true))
            }
        }

        // Check for named positions
        if (lower in namedPositions) {
            return AzimuthProperty(AzimuthValue.Named(lower))
        }

        // Try to parse as angle
        val angle = AngleParser.parse(lower)
        if (angle != null) {
            return AzimuthProperty(AzimuthValue.Angle(angle))
        }

        return AzimuthProperty(AzimuthValue.Raw(trimmed))
    }
}
