package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.MasonryAutoFlowProperty
import app.irmodels.properties.layout.grid.MasonryAutoFlowValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object MasonryAutoFlowPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return MasonryAutoFlowProperty(MasonryAutoFlowValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return MasonryAutoFlowProperty(MasonryAutoFlowValue.Raw(trimmed))
        }

        // Parse single or combined values
        val parts = lower.split(Regex("\\s+")).toSet()

        val flowValue = when {
            // Combined values (placement + ordering)
            parts.containsAll(setOf("pack", "definite-first")) -> MasonryAutoFlowValue.PackDefiniteFirst
            parts.containsAll(setOf("pack", "ordered")) -> MasonryAutoFlowValue.PackOrdered
            parts.containsAll(setOf("next", "definite-first")) -> MasonryAutoFlowValue.NextDefiniteFirst
            parts.containsAll(setOf("next", "ordered")) -> MasonryAutoFlowValue.NextOrdered
            // Single values
            parts == setOf("pack") -> MasonryAutoFlowValue.Pack
            parts == setOf("next") -> MasonryAutoFlowValue.Next
            parts == setOf("ordered") -> MasonryAutoFlowValue.Ordered
            parts == setOf("definite-first") -> MasonryAutoFlowValue.DefiniteFirst
            else -> MasonryAutoFlowValue.Raw(trimmed)
        }
        return MasonryAutoFlowProperty(flowValue)
    }
}
