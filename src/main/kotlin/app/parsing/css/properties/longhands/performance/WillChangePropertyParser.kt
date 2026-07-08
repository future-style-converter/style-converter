package app.parsing.css.properties.longhands.performance

import app.irmodels.IRProperty
import app.irmodels.properties.performance.WillChangeProperty
import app.parsing.css.properties.longhands.PropertyParser

object WillChangePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "auto") {
            return WillChangeProperty(listOf(WillChangeProperty.WillChangeValue.Auto()))
        }

        val values = trimmed.split(",").map { it.trim() }.mapNotNull { part ->
            when (part) {
                "scroll-position" -> WillChangeProperty.WillChangeValue.ScrollPosition()
                "contents" -> WillChangeProperty.WillChangeValue.Contents()
                else -> WillChangeProperty.WillChangeValue.PropertyName(part)
            }
        }

        if (values.isEmpty()) return null

        return WillChangeProperty(values)
    }
}
