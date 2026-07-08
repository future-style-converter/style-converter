package app.parsing.css.properties.longhands.counters

import app.irmodels.IRProperty
import app.irmodels.properties.counters.CounterReset
import app.irmodels.properties.counters.CounterResetProperty
import app.parsing.css.properties.longhands.PropertyParser

object CounterResetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        if (trimmed.lowercase() == "none") {
            return CounterResetProperty(emptyList())
        }

        val counters = mutableListOf<CounterReset>()
        val tokens = trimmed.split(Regex("\\s+"))

        var i = 0
        while (i < tokens.size) {
            val name = tokens[i]
            val resetValue = if (i + 1 < tokens.size) {
                tokens[i + 1].toIntOrNull()?.also { i++ } ?: 0
            } else 0
            counters.add(CounterReset(name, resetValue))
            i++
        }

        return if (counters.isNotEmpty()) CounterResetProperty(counters) else null
    }
}
