package app.parsing.css.properties.longhands.columns

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnGapProperty
import app.irmodels.properties.spacing.GapProperty.LengthPercentageOrNormal
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object ColumnGapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        val gap = when {
            lowered == "normal" -> LengthPercentageOrNormal.Normal()
            lowered.contains("(") -> LengthPercentageOrNormal.Raw(trimmed)
            else -> {
                val length = LengthParser.parse(lowered) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    LengthPercentageOrNormal.Percentage(IRPercentage(length.value))
                } else {
                    LengthPercentageOrNormal.Length(length)
                }
            }
        }

        return ColumnGapProperty(gap)
    }
}
