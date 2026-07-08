package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.AnchorValue
import app.irmodels.properties.layout.advanced.OffsetPositionProperty
import app.irmodels.properties.layout.advanced.OffsetPositionValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object OffsetPositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return OffsetPositionProperty(OffsetPositionValue.Keyword(lowered))
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return OffsetPositionProperty(OffsetPositionValue.Raw(trimmed))
        }

        // Handle auto and normal keywords
        if (lowered == "auto") {
            return OffsetPositionProperty(OffsetPositionValue.Auto)
        }
        if (lowered == "normal") {
            return OffsetPositionProperty(OffsetPositionValue.Normal)
        }

        val parts = lowered.split(Regex("\\s+"))
        val x = parseAnchorValue(parts.getOrNull(0) ?: return OffsetPositionProperty(OffsetPositionValue.Raw(trimmed)))
            ?: return OffsetPositionProperty(OffsetPositionValue.Raw(trimmed))
        val y = if (parts.size > 1) {
            parseAnchorValue(parts[1]) ?: return OffsetPositionProperty(OffsetPositionValue.Raw(trimmed))
        } else {
            // Default Y based on X value
            when (x) {
                is AnchorValue.Left, is AnchorValue.Right -> AnchorValue.Center
                is AnchorValue.Top, is AnchorValue.Bottom -> AnchorValue.Center
                else -> x
            }
        }

        return OffsetPositionProperty(x, y)
    }

    private fun parseAnchorValue(s: String): AnchorValue? {
        return when (s) {
            "auto" -> AnchorValue.Auto
            "center" -> AnchorValue.Center
            "left" -> AnchorValue.Left
            "right" -> AnchorValue.Right
            "top" -> AnchorValue.Top
            "bottom" -> AnchorValue.Bottom
            else -> {
                if (s.endsWith("%")) {
                    val percent = s.removeSuffix("%").toDoubleOrNull() ?: return null
                    AnchorValue.Percentage(IRPercentage(percent))
                } else {
                    val length = LengthParser.parse(s) ?: return null
                    AnchorValue.Length(length)
                }
            }
        }
    }
}
