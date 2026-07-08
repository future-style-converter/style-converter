package app.parsing.css.properties.longhands.background

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundPositionXProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector

object BackgroundPositionXPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle calc(), var() as raw
        if (lowered.contains("calc(") || ExpressionDetector.containsExpression(lowered)) {
            return BackgroundPositionXProperty(BackgroundPositionXProperty.PositionX.Raw(trimmed))
        }

        // Handle edge-offset syntax (e.g., "right 20px", "left 10%")
        if (trimmed.contains(" ")) {
            val parts = lowered.split("\\s+".toRegex())
            if (parts.size == 2) {
                val edge = when (parts[0]) {
                    "left" -> BackgroundPositionXProperty.HorizontalKeyword.LEFT
                    "right" -> BackgroundPositionXProperty.HorizontalKeyword.RIGHT
                    "center" -> BackgroundPositionXProperty.HorizontalKeyword.CENTER
                    else -> null
                }
                if (edge != null) {
                    val offsetStr = parts[1]
                    if (offsetStr.endsWith("%")) {
                        val pct = offsetStr.dropLast(1).toDoubleOrNull()
                        if (pct != null) {
                            return BackgroundPositionXProperty(
                                BackgroundPositionXProperty.PositionX.EdgeOffsetPercent(edge, IRPercentage(pct))
                            )
                        }
                    } else {
                        val length = LengthParser.parse(offsetStr)
                        if (length != null) {
                            return BackgroundPositionXProperty(
                                BackgroundPositionXProperty.PositionX.EdgeOffset(edge, length)
                            )
                        }
                    }
                }
            }
            return BackgroundPositionXProperty(BackgroundPositionXProperty.PositionX.Raw(trimmed))
        }

        val position = when (lowered) {
            "left" -> BackgroundPositionXProperty.PositionX.Keyword(
                BackgroundPositionXProperty.HorizontalKeyword.LEFT
            )
            "center" -> BackgroundPositionXProperty.PositionX.Keyword(
                BackgroundPositionXProperty.HorizontalKeyword.CENTER
            )
            "right" -> BackgroundPositionXProperty.PositionX.Keyword(
                BackgroundPositionXProperty.HorizontalKeyword.RIGHT
            )
            else -> {
                if (lowered.endsWith("%")) {
                    val percentValue = lowered.dropLast(1).toDoubleOrNull() ?: return null
                    BackgroundPositionXProperty.PositionX.PercentageValue(IRPercentage(percentValue))
                } else {
                    val length = LengthParser.parse(lowered) ?: return null
                    BackgroundPositionXProperty.PositionX.LengthValue(length)
                }
            }
        }
        return BackgroundPositionXProperty(position)
    }
}
