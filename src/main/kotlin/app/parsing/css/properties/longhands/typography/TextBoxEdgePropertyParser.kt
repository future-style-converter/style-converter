package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextBoxEdgeProperty
import app.irmodels.properties.typography.TextBoxEdgeValue
import app.parsing.css.properties.longhands.PropertyParser

object TextBoxEdgePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Can have one or two values: text-box-edge: leading | text-box-edge: cap alphabetic
        val parts = trimmed.split(Regex("\\s+"))

        if (parts.isEmpty() || parts.size > 2) return null

        val over = parseEdgeValue(parts[0]) ?: return null
        val under = if (parts.size == 2) {
            parseEdgeValue(parts[1]) ?: return null
        } else {
            over // If only one value, use it for both
        }

        return TextBoxEdgeProperty(over, under)
    }

    private fun parseEdgeValue(value: String): TextBoxEdgeValue? {
        return when (value) {
            "leading" -> TextBoxEdgeValue.LEADING
            "text" -> TextBoxEdgeValue.TEXT
            "cap" -> TextBoxEdgeValue.CAP
            "ex" -> TextBoxEdgeValue.EX
            "alphabetic" -> TextBoxEdgeValue.ALPHABETIC
            "ideographic" -> TextBoxEdgeValue.IDEOGRAPHIC
            "ideographic-ink" -> TextBoxEdgeValue.IDEOGRAPHIC // Map ideographic-ink to IDEOGRAPHIC
            else -> null
        }
    }
}
