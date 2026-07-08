package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.LineClampProperty
import app.parsing.css.properties.longhands.PropertyParser

object LineClampPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "none") {
            return LineClampProperty(LineClampProperty.LineClamp.None())
        }

        val lines = trimmed.toIntOrNull() ?: return null
        return LineClampProperty(LineClampProperty.LineClamp.Lines(IRNumber(lines.toDouble())))
    }
}
