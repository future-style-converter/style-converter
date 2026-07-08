package app.parsing.css.properties.longhands.counters

import app.irmodels.IRProperty
import app.irmodels.properties.counters.CounterIncrement
import app.irmodels.properties.counters.CounterIncrementProperty
import app.parsing.css.properties.longhands.PropertyParser

object CounterIncrementPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        if (trimmed.lowercase() == "none") {
            return CounterIncrementProperty(emptyList())
        }

        val counters = mutableListOf<CounterIncrement>()
        val tokens = trimmed.split(Regex("\\s+"))

        var i = 0
        while (i < tokens.size) {
            val name = tokens[i]
            val increment = if (i + 1 < tokens.size) {
                tokens[i + 1].toIntOrNull()?.also { i++ } ?: 1
            } else 1
            counters.add(CounterIncrement(name, increment))
            i++
        }

        return if (counters.isNotEmpty()) CounterIncrementProperty(counters) else null
    }
}
