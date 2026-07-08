package app.parsing.css.properties.longhands.math

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.math.MathDepthProperty
import app.irmodels.properties.math.MathDepthValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object MathDepthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return MathDepthProperty(MathDepthValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return MathDepthProperty(MathDepthValue.Raw(trimmed))
        }

        return when {
            lower == "auto" -> MathDepthProperty(MathDepthValue.Auto)
            lower == "auto-add" -> MathDepthProperty(MathDepthValue.AutoAdd)
            lower.startsWith("add(") && lower.endsWith(")") -> {
                val numStr = lower.removePrefix("add(").removeSuffix(")").trim()
                val num = numStr.toIntOrNull()
                if (num != null) {
                    MathDepthProperty(MathDepthValue.Add(num))
                } else {
                    MathDepthProperty(MathDepthValue.Raw(trimmed))
                }
            }
            else -> {
                val num = lower.toIntOrNull()
                if (num != null) {
                    MathDepthProperty(MathDepthValue.Integer(IRNumber(num.toDouble())))
                } else {
                    MathDepthProperty(MathDepthValue.Raw(trimmed))
                }
            }
        }
    }
}
