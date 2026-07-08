package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.PositionTryOption
import app.irmodels.properties.layout.advanced.PositionTryOptionsProperty
import app.parsing.css.properties.longhands.PropertyParser

object PositionTryOptionsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "none") {
            return PositionTryOptionsProperty(emptyList())
        }

        val parts = trimmed.split(Regex("\\s+"))
        val options = parts.mapNotNull { part ->
            when (part) {
                "flip-block" -> PositionTryOption.FLIP_BLOCK
                "flip-inline" -> PositionTryOption.FLIP_INLINE
                "flip-start" -> PositionTryOption.FLIP_START
                else -> null
            }
        }

        return if (options.isNotEmpty()) PositionTryOptionsProperty(options) else null
    }
}
