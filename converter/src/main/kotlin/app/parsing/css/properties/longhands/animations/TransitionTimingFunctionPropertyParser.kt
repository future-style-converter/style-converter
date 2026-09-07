package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.TimingFunction
import app.irmodels.properties.animations.TransitionTimingFunctionProperty
import app.parsing.css.properties.longhands.PropertyParser
// A6#14: this parser's private `parseCubicBezier` + `parseSteps` were
// byte-identical to the other timing-function parser's; both now call the
// shared TimingFunctionParsing object (same package, no import), which is
// also why NumberParser is no longer imported here.

/**
 * Parser for the CSS `transition-timing-function` property.
 *
 * Syntax: <timing-function> [ , <timing-function> ]*
 *
 * Uses the same timing function syntax as animation-timing-function:
 * - Keywords: linear, ease, ease-in, ease-out, ease-in-out, step-start, step-end
 * - cubic-bezier(x1, y1, x2, y2)
 * - steps(count, position?)
 */
object TransitionTimingFunctionPropertyParser : PropertyParser {
    private val KEYWORDS = setOf("linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end")

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Split by commas respecting parentheses for multiple timing functions
        val functions = splitByComma(trimmed).mapNotNull { part ->
            parseTimingFunction(part)
        }

        if (functions.isEmpty()) return null
        return TransitionTimingFunctionProperty(functions)
    }

    private fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        for (char in value) {
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char == ',' && depth == 0 -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString().trim())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString().trim())
        return result
    }

    private fun parseTimingFunction(value: String): TimingFunction? {
        val trimmed = value.trim().lowercase()

        // Handle keywords
        if (trimmed in KEYWORDS) {
            return TimingFunction.fromKeyword(trimmed)
        }

        // Handle cubic-bezier(x1, y1, x2, y2)
        if (trimmed.startsWith("cubic-bezier(") && trimmed.endsWith(")")) {
            return TimingFunctionParsing.parseCubicBezier(trimmed)
        }

        // Handle steps(count, position?)
        if (trimmed.startsWith("steps(") && trimmed.endsWith(")")) {
            return TimingFunctionParsing.parseSteps(trimmed)
        }

        return null
    }

}
