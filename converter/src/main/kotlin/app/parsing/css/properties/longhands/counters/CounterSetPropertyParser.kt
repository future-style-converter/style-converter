package app.parsing.css.properties.longhands.counters

import app.irmodels.IRProperty
import app.irmodels.properties.counters.CounterSet
import app.irmodels.properties.counters.CounterSetProperty
import app.parsing.css.properties.longhands.PropertyParser

object CounterSetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        if (trimmed.lowercase() == "none") {
            return CounterSetProperty(emptyList())
        }

        val counters = mutableListOf<CounterSet>()
        val tokens = trimmed.split(Regex("\\s+"))

        var i = 0
        while (i < tokens.size) {
            val name = tokens[i]
            val setValue = if (i + 1 < tokens.size) {
                tokens[i + 1].toIntOrNull()?.also { i++ } ?: return null
            } else return null
            counters.add(CounterSet(name, setValue))
            i++
        }

        return if (counters.isNotEmpty()) CounterSetProperty(counters) else null
    }
}
