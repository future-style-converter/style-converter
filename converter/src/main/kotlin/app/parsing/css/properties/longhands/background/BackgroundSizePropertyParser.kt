package app.parsing.css.properties.longhands.background

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundSizeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object BackgroundSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check for global keywords first (they apply to entire property, not per-layer)
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return BackgroundSizeProperty(listOf(BackgroundSizeProperty.BackgroundSize.GlobalKeyword(trimmed)))
        }

        val sizes = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            parseSize(part)
        }
        if (sizes.isEmpty()) return null
        return BackgroundSizeProperty(sizes)
    }

    private fun parseSize(value: String): BackgroundSizeProperty.BackgroundSize? {
        return when (value) {
            "cover" -> BackgroundSizeProperty.BackgroundSize.Keyword(
                BackgroundSizeProperty.BackgroundSize.SizeKeyword.COVER
            )
            "contain" -> BackgroundSizeProperty.BackgroundSize.Keyword(
                BackgroundSizeProperty.BackgroundSize.SizeKeyword.CONTAIN
            )
            "auto" -> BackgroundSizeProperty.BackgroundSize.Keyword(
                BackgroundSizeProperty.BackgroundSize.SizeKeyword.AUTO
            )
            else -> {
                val parts = value.split(Regex("\\s+"))
                if (parts.isEmpty()) return null
                
                val isPercent1 = parts[0].endsWith("%")
                val isPercent2 = parts.getOrNull(1)?.endsWith("%") == true
                
                if (isPercent1 && (parts.size == 1 || isPercent2)) {
                    val width = parts[0].dropLast(1).toDoubleOrNull() ?: return null
                    val height = parts.getOrNull(1)?.dropLast(1)?.toDoubleOrNull()
                    BackgroundSizeProperty.BackgroundSize.PercentageValue(
                        IRPercentage(width),
                        height?.let { IRPercentage(it) }
                    )
                } else {
                    val width = LengthParser.parse(parts[0]) ?: return null
                    val height = parts.getOrNull(1)?.let { LengthParser.parse(it) }
                    BackgroundSizeProperty.BackgroundSize.LengthValue(width, height)
                }
            }
        }
    }
}
