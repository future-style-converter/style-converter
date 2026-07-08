package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.VerticalAlignProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object VerticalAlignPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val alignment = when (trimmed) {
            "baseline" -> VerticalAlignProperty.VerticalAlignment.Keyword(VerticalAlignProperty.VerticalAlignment.AlignKeyword.BASELINE)
            "sub" -> VerticalAlignProperty.VerticalAlignment.Keyword(VerticalAlignProperty.VerticalAlignment.AlignKeyword.SUB)
            "super" -> VerticalAlignProperty.VerticalAlignment.Keyword(VerticalAlignProperty.VerticalAlignment.AlignKeyword.SUPER)
            "text-top" -> VerticalAlignProperty.VerticalAlignment.Keyword(VerticalAlignProperty.VerticalAlignment.AlignKeyword.TEXT_TOP)
            "text-bottom" -> VerticalAlignProperty.VerticalAlignment.Keyword(VerticalAlignProperty.VerticalAlignment.AlignKeyword.TEXT_BOTTOM)
            "middle" -> VerticalAlignProperty.VerticalAlignment.Keyword(VerticalAlignProperty.VerticalAlignment.AlignKeyword.MIDDLE)
            "top" -> VerticalAlignProperty.VerticalAlignment.Keyword(VerticalAlignProperty.VerticalAlignment.AlignKeyword.TOP)
            "bottom" -> VerticalAlignProperty.VerticalAlignment.Keyword(VerticalAlignProperty.VerticalAlignment.AlignKeyword.BOTTOM)
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    VerticalAlignProperty.VerticalAlignment.PercentageValue(IRPercentage(length.value))
                } else {
                    VerticalAlignProperty.VerticalAlignment.LengthValue(length)
                }
            }
        }

        return VerticalAlignProperty(alignment)
    }
}
