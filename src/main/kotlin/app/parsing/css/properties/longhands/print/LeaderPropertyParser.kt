package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.LeaderProperty
import app.irmodels.properties.print.LeaderValue
import app.parsing.css.properties.longhands.PropertyParser

object LeaderPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        return when (trimmed.lowercase()) {
            "dotted" -> LeaderProperty(LeaderValue.Dotted)
            "solid" -> LeaderProperty(LeaderValue.Solid)
            "space" -> LeaderProperty(LeaderValue.Space)
            else -> {
                // Handle quoted string
                val str = if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                    (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                    trimmed.removeSurrounding("\"").removeSurrounding("'")
                } else {
                    trimmed
                }
                LeaderProperty(LeaderValue.String(str))
            }
        }
    }
}
