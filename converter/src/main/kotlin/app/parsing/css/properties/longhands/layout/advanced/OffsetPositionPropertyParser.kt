package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.AnchorValue
import app.irmodels.properties.layout.advanced.OffsetPositionProperty
import app.irmodels.properties.layout.advanced.OffsetPositionValue
import app.parsing.css.properties.longhands.PropertyParser
// (IRPercentage + LengthParser are no longer imported here: their only uses
//  were inside the parseAnchorValue body moved to AnchorValueParsing.)
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

// A6#14: the private `parseAnchorValue` this file used to carry was
// byte-identical to the other offset parser's; both now call the shared
// AnchorValueParsing (same package, no import).
object OffsetPositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return OffsetPositionProperty(OffsetPositionValue.Keyword(lowered))
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return OffsetPositionProperty(OffsetPositionValue.Raw(trimmed))
        }

        // Handle auto and normal keywords
        if (lowered == "auto") {
            return OffsetPositionProperty(OffsetPositionValue.Auto)
        }
        if (lowered == "normal") {
            return OffsetPositionProperty(OffsetPositionValue.Normal)
        }

        val parts = lowered.split(Regex("\\s+"))
        val x = AnchorValueParsing.parseAnchorValue(parts.getOrNull(0) ?: return OffsetPositionProperty(OffsetPositionValue.Raw(trimmed)))
            ?: return OffsetPositionProperty(OffsetPositionValue.Raw(trimmed))
        val y = if (parts.size > 1) {
            AnchorValueParsing.parseAnchorValue(parts[1]) ?: return OffsetPositionProperty(OffsetPositionValue.Raw(trimmed))
        } else {
            // Default Y based on X value
            when (x) {
                is AnchorValue.Left, is AnchorValue.Right -> AnchorValue.Center
                is AnchorValue.Top, is AnchorValue.Bottom -> AnchorValue.Center
                else -> x
            }
        }

        return OffsetPositionProperty(x, y)
    }

}
