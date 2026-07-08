package app.parsing.css.properties.longhands.shapes

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.shapes.ShapeMarginProperty
import app.irmodels.properties.shapes.ShapeMarginValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object ShapeMarginPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return ShapeMarginProperty(ShapeMarginValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return ShapeMarginProperty(ShapeMarginValue.Raw(trimmed))
        }

        return when {
            trimmed.endsWith("%") -> {
                val percent = trimmed.removeSuffix("%").toDoubleOrNull()
                if (percent == null) {
                    ShapeMarginProperty(ShapeMarginValue.Raw(trimmed))
                } else {
                    ShapeMarginProperty(ShapeMarginValue.Percentage(IRPercentage(percent)))
                }
            }
            else -> {
                val length = LengthParser.parse(trimmed)
                if (length == null) {
                    ShapeMarginProperty(ShapeMarginValue.Raw(trimmed))
                } else {
                    ShapeMarginProperty(ShapeMarginValue.Length(length))
                }
            }
        }
    }
}
