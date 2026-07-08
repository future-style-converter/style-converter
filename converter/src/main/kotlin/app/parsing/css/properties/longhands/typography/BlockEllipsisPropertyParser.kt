package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.BlockEllipsisProperty
import app.irmodels.properties.typography.BlockEllipsisValue
import app.parsing.css.properties.longhands.PropertyParser

object BlockEllipsisPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val v = when (trimmed.lowercase()) {
            "none" -> BlockEllipsisValue.None
            "auto" -> BlockEllipsisValue.Auto
            else -> {
                // Handle quoted strings
                val unquoted = if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                    (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                    trimmed.substring(1, trimmed.length - 1)
                } else {
                    trimmed
                }
                BlockEllipsisValue.Custom(unquoted)
            }
        }
        return BlockEllipsisProperty(v)
    }
}
