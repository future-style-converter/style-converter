package app.parsing.css.properties.longhands.borders

import app.irmodels.BorderRadiusValue
import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderEndEndRadiusProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object BorderEndEndRadiusPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return BorderEndEndRadiusProperty(BorderRadiusValue.Keyword(lower), null)
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return BorderEndEndRadiusProperty(BorderRadiusValue.Raw(trimmed), null)
        }

        val parts = trimmed.split(Regex("\\s+"))
        if (parts.isEmpty()) {
            return BorderEndEndRadiusProperty(BorderRadiusValue.Raw(trimmed), null)
        }

        val horizontal = LengthParser.parse(parts[0])
        if (horizontal == null) {
            return BorderEndEndRadiusProperty(BorderRadiusValue.Raw(trimmed), null)
        }

        val vertical = if (parts.size > 1) LengthParser.parse(parts[1]) else null

        return BorderEndEndRadiusProperty(
            BorderRadiusValue.Length(horizontal),
            vertical?.let { BorderRadiusValue.Length(it) }
        )
    }
}
