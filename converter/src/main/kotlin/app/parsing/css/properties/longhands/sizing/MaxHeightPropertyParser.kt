package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.spacing.MaxHeightProperty
import app.irmodels.properties.spacing.MaxWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.CalcSizeParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object MaxHeightPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val maxValue = when {
            // css-values-5 §11 calc-size() (wave 42 lane W3) — the
            // block-axis twin of MaxWidthPropertyParser's branch; see the
            // decision record on WidthPropertyParser's.
            trimmed.startsWith("calc-size(") -> when (val r = CalcSizeParser.parse(trimmed)) {
                is CalcSizeParser.Result.Resolved ->
                    MaxWidthProperty.MaxValue.LengthValue(IRLength.fromPx(r.px))
                is CalcSizeParser.Result.Dynamic ->
                    MaxWidthProperty.MaxValue.CalcSize(r.basis, r.factor, r.offsetPx, r.original)
                null -> return null
            }
            trimmed == "none" -> MaxWidthProperty.MaxValue.None()
            trimmed == "min-content" -> MaxWidthProperty.MaxValue.MinContent()
            trimmed == "max-content" -> MaxWidthProperty.MaxValue.MaxContent()
            // css-sizing-3 §5.1 bare `fit-content` keyword (23 corpus
            // occurrences on max-height) — see WidthPropertyParser's branch.
            trimmed == "fit-content" -> MaxWidthProperty.MaxValue.FitContent(null)
            trimmed.startsWith("fit-content(") && trimmed.endsWith(")") -> {
                val sizeStr = trimmed.substring(12, trimmed.length - 1)
                val size = LengthParser.parse(sizeStr)
                MaxWidthProperty.MaxValue.FitContent(size)
            }
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    MaxWidthProperty.MaxValue.PercentageValue(IRPercentage(length.value))
                } else {
                    MaxWidthProperty.MaxValue.LengthValue(length)
                }
            }
        }
        return MaxHeightProperty(maxValue)
    }
}
