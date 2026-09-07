package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.spacing.MinWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.CalcSizeParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object MinWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val minValue = when {
            // css-values-5 §11 calc-size() (wave 42 lane W3) — see the
            // decision record on WidthPropertyParser's identical branch. On
            // min-width the runtimes resolve the `auto` basis as the
            // automatic minimum size (css-flexbox-1 §4.5 for flex items —
            // the calc-size-flex-001..003/009 shape). Unreducible calls
            // return null → Generic (web verbatim replay preserved).
            trimmed.startsWith("calc-size(") -> when (val r = CalcSizeParser.parse(trimmed)) {
                is CalcSizeParser.Result.Resolved ->
                    MinWidthProperty.MinMaxValue.LengthValue(IRLength.fromPx(r.px))
                is CalcSizeParser.Result.Dynamic ->
                    MinWidthProperty.MinMaxValue.CalcSize(r.basis, r.factor, r.offsetPx, r.original)
                null -> return null
            }
            trimmed == "auto" -> MinWidthProperty.MinMaxValue.Auto()
            trimmed == "min-content" -> MinWidthProperty.MinMaxValue.MinContent()
            trimmed == "max-content" -> MinWidthProperty.MinMaxValue.MaxContent()
            // css-sizing-3 §5.1 bare `fit-content` keyword (32 corpus
            // occurrences on min-width) — see WidthPropertyParser's branch for
            // the full decision record. MinMaxValue.FitContent(null) is the
            // same @SerialName("fit-content") variant the functional form
            // below produces, with no bounding length.
            trimmed == "fit-content" -> MinWidthProperty.MinMaxValue.FitContent(null)
            trimmed.startsWith("fit-content(") && trimmed.endsWith(")") -> {
                val sizeStr = trimmed.substring(12, trimmed.length - 1)
                val size = LengthParser.parse(sizeStr)
                MinWidthProperty.MinMaxValue.FitContent(size)
            }
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    MinWidthProperty.MinMaxValue.PercentageValue(IRPercentage(length.value))
                } else {
                    MinWidthProperty.MinMaxValue.LengthValue(length)
                }
            }
        }
        return MinWidthProperty(minValue)
    }
}
