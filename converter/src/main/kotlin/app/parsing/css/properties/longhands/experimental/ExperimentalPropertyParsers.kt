package app.parsing.css.properties.longhands.experimental

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.experimental.*
import app.parsing.css.properties.longhands.PropertyParser

object PresentationLevelPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when {
            trimmed == "same" -> PresentationLevelValue.Same
            trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.all { it.isDigit() } -> {
                val n = trimmed.toDoubleOrNull() ?: return null
                PresentationLevelValue.Increment(IRNumber(n))
            }
            else -> return null
        }
        return PresentationLevelProperty(v)
    }
}

object RunningPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        return RunningProperty(RunningValue.Named(trimmed))
    }
}

object StringSetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"), limit = 2)
        if (parts.isEmpty()) return null
        val name = parts[0]
        val v = if (parts.size > 1) parts[1] else ""
        return StringSetProperty(name, v)
    }
}
