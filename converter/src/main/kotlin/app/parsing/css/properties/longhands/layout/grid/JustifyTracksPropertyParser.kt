package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.JustifyTracksProperty
import app.irmodels.properties.layout.grid.JustifyTracksValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object JustifyTracksPropertyParser : PropertyParser {
    private val validTrackValues = setOf(
        "normal", "start", "end", "center", "stretch",
        "space-between", "space-around", "space-evenly"
    )

    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return JustifyTracksProperty(JustifyTracksValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return JustifyTracksProperty(JustifyTracksValue.Raw(trimmed))
        }

        // Parse multi-value (space-separated list of justify values)
        val parts = lower.split(Regex("\\s+"))
        if (parts.size > 1 && parts.all { it in validTrackValues }) {
            return JustifyTracksProperty(JustifyTracksValue.Multi(parts))
        }

        // Single value
        val justifyValue = when (lower) {
            "normal" -> JustifyTracksValue.Normal
            "start" -> JustifyTracksValue.Start
            "end" -> JustifyTracksValue.End
            "center" -> JustifyTracksValue.Center
            "stretch" -> JustifyTracksValue.Stretch
            "space-between" -> JustifyTracksValue.SpaceBetween
            "space-around" -> JustifyTracksValue.SpaceAround
            "space-evenly" -> JustifyTracksValue.SpaceEvenly
            else -> JustifyTracksValue.Raw(trimmed)
        }
        return JustifyTracksProperty(justifyValue)
    }
}
