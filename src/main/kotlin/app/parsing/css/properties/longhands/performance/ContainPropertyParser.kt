package app.parsing.css.properties.longhands.performance

import app.irmodels.IRProperty
import app.irmodels.properties.performance.ContainProperty
import app.parsing.css.properties.longhands.PropertyParser

object ContainPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "none") {
            return ContainProperty(listOf(ContainProperty.ContainValue.NONE))
        }

        val values = trimmed.split(Regex("\\s+")).mapNotNull { part ->
            when (part) {
                "strict" -> ContainProperty.ContainValue.STRICT
                "content" -> ContainProperty.ContainValue.CONTENT
                "size" -> ContainProperty.ContainValue.SIZE
                "layout" -> ContainProperty.ContainValue.LAYOUT
                "style" -> ContainProperty.ContainValue.STYLE
                "paint" -> ContainProperty.ContainValue.PAINT
                "inline-size" -> ContainProperty.ContainValue.INLINE_SIZE
                else -> null
            }
        }

        if (values.isEmpty()) return null

        return ContainProperty(values)
    }
}
