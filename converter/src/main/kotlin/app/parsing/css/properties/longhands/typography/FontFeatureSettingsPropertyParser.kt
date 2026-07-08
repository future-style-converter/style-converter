package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontFeatureSettingsProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object FontFeatureSettingsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return FontFeatureSettingsProperty(FontFeatureSettingsProperty.FeatureSettings.Keyword(lowered))
        }

        if (lowered == "normal") {
            return FontFeatureSettingsProperty(FontFeatureSettingsProperty.FeatureSettings.Normal())
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return FontFeatureSettingsProperty(FontFeatureSettingsProperty.FeatureSettings.Raw(trimmed))
        }

        // Parse feature tags: "liga" 1, "dlig" 0, etc.
        val features = mutableListOf<FontFeatureSettingsProperty.Feature>()
        val parts = lowered.split(",").map { it.trim() }

        for (part in parts) {
            val tokens = part.split(Regex("\\s+"))
            if (tokens.isEmpty()) continue

            val tag = tokens[0].removeSurrounding("\"").removeSurrounding("'")
            if (tag.length != 4) {
                // Fall back to Raw for unparseable values
                return FontFeatureSettingsProperty(FontFeatureSettingsProperty.FeatureSettings.Raw(trimmed))
            }

            val featureValue = if (tokens.size > 1) {
                val parsed = tokens[1].toIntOrNull()
                if (parsed == null) {
                    // Handle "on" and "off" as 1 and 0
                    when (tokens[1]) {
                        "on" -> 1
                        "off" -> 0
                        else -> return FontFeatureSettingsProperty(FontFeatureSettingsProperty.FeatureSettings.Raw(trimmed))
                    }
                } else parsed
            } else {
                null
            }

            features.add(FontFeatureSettingsProperty.Feature(tag, featureValue))
        }

        if (features.isEmpty()) {
            return FontFeatureSettingsProperty(FontFeatureSettingsProperty.FeatureSettings.Raw(trimmed))
        }

        return FontFeatureSettingsProperty(FontFeatureSettingsProperty.FeatureSettings.Features(features))
    }
}
