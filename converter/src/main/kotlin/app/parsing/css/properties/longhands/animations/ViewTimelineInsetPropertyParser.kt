package app.parsing.css.properties.longhands.animations

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.animations.ViewTimelineInsetProperty
import app.irmodels.properties.animations.ViewTimelineInsetValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object ViewTimelineInsetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return null

        val start = parseInsetValue(parts[0]) ?: return null
        val end = if (parts.size > 1) parseInsetValue(parts[1]) ?: return null else start

        return ViewTimelineInsetProperty(start, end)
    }

    private fun parseInsetValue(s: String): ViewTimelineInsetValue? {
        val trimmed = s.trim().lowercase()
        return when {
            trimmed == "auto" -> ViewTimelineInsetValue.Auto
            trimmed.endsWith("%") -> {
                val percent = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
                ViewTimelineInsetValue.Percentage(IRPercentage(percent))
            }
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                ViewTimelineInsetValue.Length(length)
            }
        }
    }
}
