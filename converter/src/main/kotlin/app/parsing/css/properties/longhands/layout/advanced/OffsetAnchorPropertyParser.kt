package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.AnchorValue
import app.irmodels.properties.layout.advanced.OffsetAnchorProperty
import app.irmodels.properties.layout.advanced.OffsetAnchorValue
import app.parsing.css.properties.longhands.PropertyParser
// (IRPercentage + LengthParser are no longer imported here: their only uses
//  were inside the parseAnchorValue body moved to AnchorValueParsing.)
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

// A6#14: the private `parseAnchorValue` this file used to carry was
// byte-identical to the other offset parser's; both now call the shared
// AnchorValueParsing (same package, no import).
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
        val x = AnchorValueParsing.parseAnchorValue(parts.getOrNull(0) ?: return OffsetAnchorProperty(OffsetAnchorValue.Raw(trimmed)))
            ?: return OffsetAnchorProperty(OffsetAnchorValue.Raw(trimmed))
        val y = if (parts.size > 1) {
            AnchorValueParsing.parseAnchorValue(parts[1]) ?: return OffsetAnchorProperty(OffsetAnchorValue.Raw(trimmed))
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

}
