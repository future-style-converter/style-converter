package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationTimingFunctionProperty
import app.irmodels.properties.animations.TimingFunction
import app.parsing.css.properties.longhands.PropertyParser
// A6#14: this parser's private `parseCubicBezier` + `parseSteps` were
// byte-identical to the other timing-function parser's; both now call the
// shared TimingFunctionParsing object (same package, no import), which is
// also why NumberParser is no longer imported here.

/**
 * Parser for the CSS `animation-timing-function` property.
 *
 * Syntax: <timing-function> [ , <timing-function> ]*
 *
 * Timing functions:
 * - Keywords: linear, ease, ease-in, ease-out, ease-in-out, step-start, step-end
 * - cubic-bezier(x1, y1, x2, y2)
 * - steps(count, position?)
 * - linear(stop, stop, ...)
 */
object AnimationTimingFunctionPropertyParser : PropertyParser {
    private val KEYWORDS = setOf("linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end")

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Split by commas, but respect parentheses (don't split inside functions)
        val parts = splitByCommaRespectingParens(trimmed)
        val functions = parts.mapNotNull { part ->
            parseTimingFunction(part)
        }

        if (functions.isEmpty()) return null
        return AnimationTimingFunctionProperty(functions)
    }

    private fun splitByCommaRespectingParens(value: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                }
                char == ',' && parenDepth == 0 -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }

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

        // Handle linear(stop, stop, ...) function (NOT the keyword)
        if (trimmed.startsWith("linear(") && trimmed.endsWith(")")) {
            return parseLinear(trimmed)
        }

        return null
    }

    private fun parseLinear(value: String): TimingFunction? {
        // Extract content: linear(0, 0.25, 1) or linear(0, 0.5 25% 75%, 1)
        val content = value.substringAfter("linear(").substringBefore(")")
        val parts = content.split(Regex("\\s*,\\s*"))

        if (parts.isEmpty()) return null

        val stops = parts.mapNotNull { part ->
            parseLinearStop(part.trim())
        }

        if (stops.isEmpty()) return null
        return TimingFunction.fromLinear(stops)
    }

    private fun parseLinearStop(part: String): TimingFunction.LinearStop? {
        // Format: value or value position% or value start% end%
        val tokens = part.split(Regex("\\s+"))
        if (tokens.isEmpty()) return null

        val value = tokens[0].toDoubleOrNull() ?: return null

        // Parse optional position (take first percentage if present)
        val position = if (tokens.size > 1) {
            val posStr = tokens[1]
            if (posStr.endsWith("%")) {
                posStr.dropLast(1).toDoubleOrNull()
            } else null
        } else null

        return TimingFunction.LinearStop(value, position)
    }

}
