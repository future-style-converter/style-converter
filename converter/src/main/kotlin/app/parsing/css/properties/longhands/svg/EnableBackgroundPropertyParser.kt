package app.parsing.css.properties.longhands.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.svg.EnableBackgroundProperty
import app.irmodels.properties.svg.EnableBackgroundValue
import app.parsing.css.properties.longhands.PropertyParser

object EnableBackgroundPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()

        // Handle "accumulate" keyword
        if (normalized == "accumulate") {
            return EnableBackgroundProperty(EnableBackgroundValue.Accumulate)
        }

        // Handle "new" keyword with optional coordinates
        if (normalized.startsWith("new")) {
            val parts = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }

            // Just "new" without coordinates
            if (parts.size == 1) {
                return EnableBackgroundProperty(EnableBackgroundValue.New())
            }

            // "new x y width height"
            if (parts.size == 5) {
                val x = parts[1].toDoubleOrNull() ?: return null
                val y = parts[2].toDoubleOrNull() ?: return null
                val width = parts[3].toDoubleOrNull() ?: return null
                val height = parts[4].toDoubleOrNull() ?: return null

                return EnableBackgroundProperty(
                    EnableBackgroundValue.New(
                        x = IRNumber(x),
                        y = IRNumber(y),
                        width = IRNumber(width),
                        height = IRNumber(height)
                    )
                )
            }

            return null
        }

        return null
    }
}
