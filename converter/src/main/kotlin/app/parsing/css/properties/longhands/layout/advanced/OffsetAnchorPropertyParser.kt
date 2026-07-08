package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.AnchorValue
import app.irmodels.properties.layout.advanced.OffsetAnchorProperty
import app.irmodels.properties.layout.advanced.OffsetAnchorValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object OffsetAnchorPropertyParser : PropertyParser {
    private val positionKeywords = setOf("center", "left", "right", "top", "bottom")

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return OffsetAnchorProperty(OffsetAnchorValue.Keyword(lowered))
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return OffsetAnchorProperty(OffsetAnchorValue.Raw(trimmed))
        }

        // Handle "auto" keyword
        if (lowered == "auto") {
            return OffsetAnchorProperty(OffsetAnchorValue.Auto)
        }

        val parts = lowered.split(Regex("\\s+"))
        val x = parseAnchorValue(parts.getOrNull(0) ?: return OffsetAnchorProperty(OffsetAnchorValue.Raw(trimmed)))
            ?: return OffsetAnchorProperty(OffsetAnchorValue.Raw(trimmed))
        val y = if (parts.size > 1) {
            parseAnchorValue(parts[1]) ?: return OffsetAnchorProperty(OffsetAnchorValue.Raw(trimmed))
        } else {
            // Default Y based on X value
            when (x) {
                is AnchorValue.Left, is AnchorValue.Right -> AnchorValue.Center
                is AnchorValue.Top, is AnchorValue.Bottom -> AnchorValue.Center
                else -> x
            }
        }

        return OffsetAnchorProperty(x, y)
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
