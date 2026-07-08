package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.transforms.ScaleProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object ScalePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return ScaleProperty(ScaleProperty.Scale.Keyword(lower))
        }

        // Handle CSS expressions (var, env, calc)
        if (isExpression(lower)) {
            return ScaleProperty(ScaleProperty.Scale.Raw(trimmed))
        }

        // Handle "none" keyword
        if (lower == "none") {
            return ScaleProperty(ScaleProperty.Scale.None())
        }

        // Parse numeric values (including percentages)
        val parts = lower.split(Regex("\\s+"))
        return when (parts.size) {
            1 -> {
                val num = parseScaleValue(parts[0])
                if (num != null) {
                    ScaleProperty(ScaleProperty.Scale.Uniform(IRNumber(num)))
                } else {
                    ScaleProperty(ScaleProperty.Scale.Raw(trimmed))
                }
            }
            2 -> {
                val x = parseScaleValue(parts[0])
                val y = parseScaleValue(parts[1])
                if (x != null && y != null) {
                    ScaleProperty(ScaleProperty.Scale.TwoAxis(IRNumber(x), IRNumber(y)))
                } else {
                    ScaleProperty(ScaleProperty.Scale.Raw(trimmed))
                }
            }
            3 -> {
                val x = parseScaleValue(parts[0])
                val y = parseScaleValue(parts[1])
                val z = parseScaleValue(parts[2])
                if (x != null && y != null && z != null) {
                    ScaleProperty(ScaleProperty.Scale.ThreeAxis(IRNumber(x), IRNumber(y), IRNumber(z)))
                } else {
                    ScaleProperty(ScaleProperty.Scale.Raw(trimmed))
                }
            }
            else -> {
                ScaleProperty(ScaleProperty.Scale.Raw(trimmed))
            }
        }
    }

    private fun parseScaleValue(value: String): Double? {
        // Handle percentages (150% -> 1.5)
        if (value.endsWith("%")) {
            val numStr = value.dropLast(1)
            val num = numStr.toDoubleOrNull()
            return num?.div(100.0)
        }
        // Handle plain numbers
        return value.toDoubleOrNull()
    }

    private fun isExpression(value: String): Boolean {
        return ExpressionDetector.startsWithExpression(value) ||
               value.startsWith("clamp(") ||
               value.startsWith("min(") ||
               value.startsWith("max(")
    }
}
