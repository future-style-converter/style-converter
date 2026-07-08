package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.OffsetRotateProperty
import app.irmodels.properties.layout.advanced.OffsetRotateValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object OffsetRotatePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return OffsetRotateProperty(OffsetRotateValue.Keyword(lowered))
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return OffsetRotateProperty(OffsetRotateValue.Raw(trimmed))
        }

        // Simple keywords
        if (lowered == "auto") {
            return OffsetRotateProperty(OffsetRotateValue.Auto)
        }
        if (lowered == "reverse") {
            return OffsetRotateProperty(OffsetRotateValue.Reverse)
        }

        // Split into parts for combined values like "auto 45deg" or "reverse 90deg"
        val parts = lowered.split(Regex("\\s+"))

        if (parts.size == 1) {
            // Single angle
            val angle = AngleParser.parse(parts[0])
            return if (angle != null) {
                OffsetRotateProperty(OffsetRotateValue.Angle(angle))
            } else {
                OffsetRotateProperty(OffsetRotateValue.Raw(trimmed))
            }
        }

        if (parts.size == 2) {
            // Combined: "auto 45deg" or "reverse 90deg" or "45deg auto"
            val hasAuto = "auto" in parts
            val hasReverse = "reverse" in parts
            val anglePart = parts.find { it != "auto" && it != "reverse" }

            if ((hasAuto || hasReverse) && anglePart != null) {
                val angle = AngleParser.parse(anglePart)
                if (angle != null) {
                    return OffsetRotateProperty(OffsetRotateValue.AutoAngle(hasAuto, hasReverse, angle))
                }
            }
        }

        // Fall back to Raw for unparseable values
        return OffsetRotateProperty(OffsetRotateValue.Raw(trimmed))
    }
}
