package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.borders.BoxShadowProperty
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Parser for `box-shadow` property.
 */
object BoxShadowPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle none
        if (lower == "none") {
            return BoxShadowProperty(emptyList())
        }

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return BoxShadowProperty(BoxShadowProperty.BoxShadowValue.Keyword(lower))
        }

        // Handle expressions (calc, var, etc.)
        if (ExpressionDetector.containsExpression(lower)) {
            return BoxShadowProperty(BoxShadowProperty.BoxShadowValue.Expression(trimmed))
        }

        val shadowStrings = TokenizationUtils.splitByComma(trimmed)
        val shadows = shadowStrings.mapNotNull { parseSingleShadow(it) }
        if (shadows.isEmpty()) return null
        return BoxShadowProperty(shadows)
    }

    private fun parseSingleShadow(value: String): BoxShadowProperty.Shadow? {
        val tokens = TokenizationUtils.tokenizeByWhitespace(value)
        if (tokens.size < 2) return null
        var inset = false
        val lengths = mutableListOf<app.irmodels.IRLength>()
        var color: app.irmodels.IRColor? = null
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.lowercase() == "inset" -> {
                    inset = true
                }
                else -> {
                    val length = LengthParser.parse(token)
                    if (length != null) {
                        lengths.add(length)
                    } else {
                        val parsedColor = ColorParser.parse(token)
                        if (parsedColor != null) {
                            color = parsedColor
                        }
                    }
                }
            }
            i++
        }
        if (lengths.size < 2) return null
        val offsetX = lengths[0]
        val offsetY = lengths[1]
        val blurRadius = lengths.getOrNull(2)
        val spreadRadius = lengths.getOrNull(3)
        return BoxShadowProperty.Shadow(
            offsetX = offsetX,
            offsetY = offsetY,
            blurRadius = blurRadius,
            spreadRadius = spreadRadius,
            color = color,
            inset = inset
        )
    }
}
