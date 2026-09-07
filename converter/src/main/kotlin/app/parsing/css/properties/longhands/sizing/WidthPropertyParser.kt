package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.spacing.WidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.CalcSizeParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object WidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        val widthValue = when {
            lower == "auto" -> WidthProperty.WidthValue.Auto()
            lower == "min-content" -> WidthProperty.WidthValue.MinContent()
            lower == "max-content" -> WidthProperty.WidthValue.MaxContent()
            // css-sizing-3 §5.1 lists `fit-content` as a BARE keyword alongside
            // the `fit-content(<length-percentage>)` function below — the
            // keyword form is `fit-content(stretch)`, i.e. an unbounded
            // shrink-to-fit. Only the FUNCTIONAL form was recognised here, so
            // the single commonest intrinsic declaration in the WPT corpus
            // (`width: fit-content`, 148 occurrences) fell through to
            // `LengthParser.parse` → null → the Generic degradation envelope,
            // where every runtime dropped it. WidthValue.FitContent(null)
            // already serialises to the bare `"fit-content"` primitive (see
            // WidthValueSerializer) and every runtime's LengthValue decoder
            // already reads that string, so this is a parser hole, not a wire
            // change. The logical twins (InlineSizePropertyParser,
            // MaxInlineSizePropertyParser, MinBlockSizePropertyParser) have
            // carried this branch since they were written — this restores
            // parity on the physical axis.
            lower == "fit-content" -> WidthProperty.WidthValue.FitContent(null)
            lower.startsWith("fit-content(") && lower.endsWith(")") -> {
                val sizeStr = lower.substring(12, lower.length - 1)
                val size = LengthParser.parse(sizeStr)
                WidthProperty.WidthValue.FitContent(size)
            }
            lower.startsWith("anchor-size(") && lower.endsWith(")") -> {
                parseAnchorSize(trimmed)
            }
            // css-values-5 §11 calc-size() (wave 42 lane W3). Must sit
            // BEFORE the isExpression branch so it is typed rather than
            // preserved as an opaque Expression string. A parse-time-
            // evaluable call (pure-length basis / size-free expr) collapses
            // to a plain length; an affine `size` expr over a keyword basis
            // becomes the typed CalcSize value the runtimes resolve; every
            // other shape (min(size,…), percent basis) returns null → the
            // Generic envelope, keeping web's verbatim replay unchanged.
            lower.startsWith("calc-size(") -> when (val r = CalcSizeParser.parse(trimmed)) {
                is CalcSizeParser.Result.Resolved ->
                    WidthProperty.WidthValue.LengthValue(IRLength.fromPx(r.px))
                is CalcSizeParser.Result.Dynamic ->
                    WidthProperty.WidthValue.CalcSize(r.basis, r.factor, r.offsetPx, r.original)
                null -> return null
            }
            // Handle calc(), clamp(), min(), max(), var(), env() and math function expressions
            LengthParser.isExpression(lower) -> WidthProperty.WidthValue.Expression(trimmed)
            else -> {
                val length = LengthParser.parse(lower) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    WidthProperty.WidthValue.PercentageValue(IRPercentage(length.value))
                } else {
                    WidthProperty.WidthValue.LengthValue(length)
                }
            }
        }

        return WidthProperty(widthValue)
    }

    private fun parseAnchorSize(value: String): WidthProperty.WidthValue.AnchorSize {
        // Formats: anchor-size(dimension) or anchor-size(--name dimension)
        val content = value.substringAfter("anchor-size(").substringBefore(")").trim()
        val tokens = content.split(Regex("\\s+"))
        return if (tokens.size >= 2 && tokens[0].startsWith("--")) {
            WidthProperty.WidthValue.AnchorSize(anchorName = tokens[0], dimension = tokens[1])
        } else {
            WidthProperty.WidthValue.AnchorSize(anchorName = null, dimension = tokens.getOrElse(0) { "width" })
        }
    }
}
