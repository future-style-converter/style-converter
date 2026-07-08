package app.parsing.css.properties.longhands.columns

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnCountProperty
import app.parsing.css.properties.longhands.PropertyParser

object ColumnCountPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val count = when {
            trimmed == "auto" -> ColumnCountProperty.ColumnCount.Auto()
            else -> {
                val num = trimmed.toIntOrNull() ?: return null
                if (num < 1) return null
                ColumnCountProperty.ColumnCount.Number(IRNumber(num.toDouble()))
            }
        }

        return ColumnCountProperty(count)
    }
}
