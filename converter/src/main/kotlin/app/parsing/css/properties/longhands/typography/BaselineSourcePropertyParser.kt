package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.BaselineSourceProperty
import app.irmodels.properties.typography.BaselineSourceValue
import app.parsing.css.properties.longhands.PropertyParser

object BaselineSourcePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val sourceValue = when (trimmed) {
            "auto" -> BaselineSourceValue.AUTO
            "first" -> BaselineSourceValue.FIRST
            "last" -> BaselineSourceValue.LAST
            else -> return null
        }
        return BaselineSourceProperty(sourceValue)
    }
}
