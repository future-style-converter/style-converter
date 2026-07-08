package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.VerticalAlignLastProperty
import app.irmodels.properties.typography.VerticalAlignLastValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

object VerticalAlignLastPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val alignValue = when (trimmed) {
            "auto" -> VerticalAlignLastValue.Auto
            "baseline" -> VerticalAlignLastValue.Baseline
            "top" -> VerticalAlignLastValue.Top
            "middle" -> VerticalAlignLastValue.Middle
            "bottom" -> VerticalAlignLastValue.Bottom
            "text-top" -> VerticalAlignLastValue.Top
            "text-bottom" -> VerticalAlignLastValue.Bottom
            "sub", "super" -> VerticalAlignLastValue.Baseline
            else -> {
                PercentageParser.parse(trimmed)?.let {
                    return VerticalAlignLastProperty(VerticalAlignLastValue.Percentage(it))
                }
                LengthParser.parse(trimmed)?.let {
                    return VerticalAlignLastProperty(VerticalAlignLastValue.Length(it))
                }
                return null
            }
        }

        return VerticalAlignLastProperty(alignValue)
    }
}
