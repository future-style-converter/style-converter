package app.parsing.css.properties.longhands.layout.position

import app.irmodels.IRProperty
import app.irmodels.properties.layout.position.ZIndex
import app.irmodels.properties.layout.position.ZIndexProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object ZIndexPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        val zIndex = when {
            // Handle 'auto' keyword
            lower == "auto" -> ZIndex.auto()

            // Handle global keywords
            GlobalKeywords.isGlobalKeyword(lower) -> ZIndex.fromGlobalKeyword(lower)

            // Handle expressions (calc, var, etc.)
            ExpressionDetector.containsExpression(lower) -> ZIndex.fromExpression(trimmed)

            // Try parsing as integer
            else -> {
                val intValue = trimmed.toIntOrNull() ?: return null
                ZIndex.fromInteger(intValue)
            }
        }

        return ZIndexProperty(zIndex)
    }
}
