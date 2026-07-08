package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.transforms.TranslateProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object TranslatePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none") {
            return TranslateProperty(TranslateProperty.Translate.None())
        }

        val parts = trimmed.split(Regex("\\s+"))
        return when (parts.size) {
            1 -> {
                val lp = parseLengthPercentage(parts[0]) ?: return null
                TranslateProperty(TranslateProperty.Translate.OneAxis(lp))
            }
            2 -> {
                val x = parseLengthPercentage(parts[0]) ?: return null
                val y = parseLengthPercentage(parts[1]) ?: return null
                TranslateProperty(TranslateProperty.Translate.TwoAxis(x, y))
            }
            3 -> {
                val x = parseLengthPercentage(parts[0]) ?: return null
                val y = parseLengthPercentage(parts[1]) ?: return null
                val z = LengthParser.parse(parts[2]) ?: return null
                TranslateProperty(TranslateProperty.Translate.ThreeAxis(x, y, z))
            }
            else -> null
        }
    }

    private fun parseLengthPercentage(s: String): TranslateProperty.LengthPercentage? {
        return if (s.endsWith("%")) {
            val percent = s.removeSuffix("%").toDoubleOrNull() ?: return null
            TranslateProperty.LengthPercentage.PercentageValue(IRPercentage(percent))
        } else {
            val length = LengthParser.parse(s) ?: return null
            TranslateProperty.LengthPercentage.LengthValue(length)
        }
    }
}
