package app.parsing.css.properties.longhands.typography

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.typography.InitialLetterProperty
import app.irmodels.properties.typography.InitialLetterValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser

/**
 * Parser for `initial-letter` property.
 *
 * Values: normal | <number> [<integer>]?
 * First number is size (how many lines the initial letter occupies)
 * Optional second integer is sink (how many lines it sinks into)
 */
object InitialLetterPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'normal' keyword
        if (trimmed == "normal") {
            return InitialLetterProperty(InitialLetterValue.Normal)
        }

        // Parse numeric values
        val parts = trimmed.split(Regex("""\s+"""))
        if (parts.isEmpty()) return null

        val size = NumberParser.parse(parts[0]) ?: return null
        val sink = if (parts.size > 1) {
            NumberParser.parse(parts[1])
        } else {
            null
        }

        return InitialLetterProperty(InitialLetterValue.Size(size, sink))
    }
}
