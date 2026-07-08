package app.parsing.css.properties.longhands.background

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundPositionYProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector

object BackgroundPositionYPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle calc(), var() as raw
        if (lowered.contains("calc(") || ExpressionDetector.containsExpression(lowered)) {
            return BackgroundPositionYProperty(BackgroundPositionYProperty.PositionY.Raw(trimmed))
        }

        // Handle edge-offset syntax (e.g., "bottom 10px", "top 20%")
        if (trimmed.contains(" ")) {
            val parts = lowered.split("\\s+".toRegex())
            if (parts.size == 2) {
                val edge = when (parts[0]) {
                    "top" -> BackgroundPositionYProperty.VerticalKeyword.TOP
                    "bottom" -> BackgroundPositionYProperty.VerticalKeyword.BOTTOM
                    "center" -> BackgroundPositionYProperty.VerticalKeyword.CENTER
                    else -> null
                }
                if (edge != null) {
                    val offsetStr = parts[1]
                    if (offsetStr.endsWith("%")) {
                        val pct = offsetStr.dropLast(1).toDoubleOrNull()
                        if (pct != null) {
                            return BackgroundPositionYProperty(
                                BackgroundPositionYProperty.PositionY.EdgeOffsetPercent(edge, IRPercentage(pct))
                            )
                        }
                    } else {
                        val length = LengthParser.parse(offsetStr)
                        if (length != null) {
                            return BackgroundPositionYProperty(
                                BackgroundPositionYProperty.PositionY.EdgeOffset(edge, length)
                            )
                        }
                    }
                }
            }
            return BackgroundPositionYProperty(BackgroundPositionYProperty.PositionY.Raw(trimmed))
        }

        val position = when (lowered) {
            "top" -> BackgroundPositionYProperty.PositionY.Keyword(
                BackgroundPositionYProperty.VerticalKeyword.TOP
            )
            "center" -> BackgroundPositionYProperty.PositionY.Keyword(
                BackgroundPositionYProperty.VerticalKeyword.CENTER
            )
            "bottom" -> BackgroundPositionYProperty.PositionY.Keyword(
                BackgroundPositionYProperty.VerticalKeyword.BOTTOM
            )
            else -> {
                if (lowered.endsWith("%")) {
                    val percentValue = lowered.dropLast(1).toDoubleOrNull() ?: return null
                    BackgroundPositionYProperty.PositionY.PercentageValue(IRPercentage(percentValue))
                } else {
                    val length = LengthParser.parse(lowered) ?: return null
                    BackgroundPositionYProperty.PositionY.LengthValue(length)
                }
            }
        }
        return BackgroundPositionYProperty(position)
    }
}
