package app.parsing.css.properties.longhands.sizing

import app.irmodels.IRProperty
import app.irmodels.properties.spacing.AspectRatioProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object AspectRatioPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle auto keyword
        if (lower == "auto") {
            return AspectRatioProperty.auto()
        }

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return AspectRatioProperty.fromKeyword(lower)
        }

        // Check for expressions (calc, var, etc.)
        if (ExpressionDetector.containsExpression(lower)) {
            return AspectRatioProperty.fromExpression(trimmed)
        }

        // Handle "auto width / height" combo syntax
        if (lower.startsWith("auto ")) {
            val rest = lower.removePrefix("auto ").trim()
            val ratioParts = rest.split("/")
            if (ratioParts.size == 2) {
                val width = ratioParts[0].trim().toDoubleOrNull()
                val height = ratioParts[1].trim().toDoubleOrNull()
                if (width != null && height != null) {
                    return AspectRatioProperty.fromAutoRatio(width, height)
                }
            }
        }

        // Ratio: width/height
        val parts = lower.split("/")
        if (parts.size == 2) {
            val width = parts[0].trim().toDoubleOrNull()
            val height = parts[1].trim().toDoubleOrNull()
            if (width != null && height != null) {
                return AspectRatioProperty.fromRatio(width, height)
            }
        }

        // Single value (e.g., "1.5" means 1.5/1)
        val single = lower.toDoubleOrNull()
        if (single != null) {
            return AspectRatioProperty.fromSingleValue(single)
        }

        return null
    }
}
