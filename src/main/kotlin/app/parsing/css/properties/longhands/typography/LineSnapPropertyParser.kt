package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.LineSnapProperty
import app.irmodels.properties.typography.LineSnapValue
import app.parsing.css.properties.longhands.PropertyParser

object LineSnapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val snapValue = when (trimmed) {
            "none" -> LineSnapValue.NONE
            "baseline" -> LineSnapValue.BASELINE
            "contain" -> LineSnapValue.CONTAIN
            else -> return null
        }

        return LineSnapProperty(snapValue)
    }
}
