package app.parsing.css.properties.longhands.effects

import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.MaskBorderSliceValue
import app.irmodels.SliceComponent
import app.irmodels.properties.effects.MaskBorderSliceProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object MaskBorderSlicePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return MaskBorderSliceProperty(MaskBorderSliceValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return MaskBorderSliceProperty(MaskBorderSliceValue.Raw(trimmed))
        }

        // Parse tokens
        val tokens = trimmed.split(Regex("\\s+"))
        val hasFill = tokens.any { it.lowercase() == "fill" }
        val valueTokens = tokens.filter { it.lowercase() != "fill" }

        // Handle single "fill" keyword
        if (valueTokens.isEmpty() && hasFill) {
            return MaskBorderSliceProperty(MaskBorderSliceValue.Keyword("fill"))
        }

        // Parse slice components
        val components = valueTokens.mapNotNull { parseComponent(it) }
        if (components.isEmpty()) {
            return MaskBorderSliceProperty(MaskBorderSliceValue.Raw(trimmed))
        }

        // Single value case (no fill)
        if (components.size == 1 && !hasFill) {
            val comp = components[0]
            return when (comp) {
                is SliceComponent.Num -> MaskBorderSliceProperty(MaskBorderSliceValue.Number(IRNumber(comp.value)))
                is SliceComponent.Pct -> MaskBorderSliceProperty(MaskBorderSliceValue.Percentage(IRPercentage(comp.value)))
            }
        }

        // Multi-value or has fill - use Values variant
        val (top, right, bottom, left) = when (components.size) {
            1 -> listOf(components[0], components[0], components[0], components[0])
            2 -> listOf(components[0], components[1], components[0], components[1])
            3 -> listOf(components[0], components[1], components[2], components[1])
            else -> listOf(components[0], components[1], components[2], components[3])
        }

        return MaskBorderSliceProperty(MaskBorderSliceValue.Values(top, right, bottom, left, hasFill))
    }

    private fun parseComponent(token: String): SliceComponent? {
        return when {
            token.endsWith("%") -> {
                val pct = token.removeSuffix("%").toDoubleOrNull()
                if (pct != null) SliceComponent.Pct(pct) else null
            }
            else -> {
                val num = token.toDoubleOrNull()
                if (num != null) SliceComponent.Num(num) else null
            }
        }
    }
}
