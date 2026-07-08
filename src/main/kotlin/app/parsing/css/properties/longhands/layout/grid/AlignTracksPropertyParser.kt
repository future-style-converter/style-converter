package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.AlignTracksProperty
import app.irmodels.properties.layout.grid.AlignTracksValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object AlignTracksPropertyParser : PropertyParser {
    private val validTrackValues = setOf(
        "normal", "start", "end", "center", "stretch",
        "space-between", "space-around", "space-evenly", "baseline"
    )

    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return AlignTracksProperty(AlignTracksValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return AlignTracksProperty(AlignTracksValue.Raw(trimmed))
        }

        // Parse multi-value (space-separated list of alignment values)
        val parts = lower.split(Regex("\\s+"))
        if (parts.size > 1 && parts.all { it in validTrackValues }) {
            return AlignTracksProperty(AlignTracksValue.Multi(parts))
        }

        // Single value
        val alignValue = when (lower) {
            "normal" -> AlignTracksValue.Normal
            "start" -> AlignTracksValue.Start
            "end" -> AlignTracksValue.End
            "center" -> AlignTracksValue.Center
            "stretch" -> AlignTracksValue.Stretch
            "space-between" -> AlignTracksValue.SpaceBetween
            "space-around" -> AlignTracksValue.SpaceAround
            "space-evenly" -> AlignTracksValue.SpaceEvenly
            "baseline" -> AlignTracksValue.Baseline
            else -> AlignTracksValue.Raw(trimmed)
        }
        return AlignTracksProperty(alignValue)
    }
}
