package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationTimingFunctionProperty
import app.irmodels.properties.animations.TimingFunction
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser

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
            return parseCubicBezier(trimmed)
        }

        // Handle steps(count, position?)
        if (trimmed.startsWith("steps(") && trimmed.endsWith(")")) {
            return parseSteps(trimmed)
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

    private fun parseCubicBezier(value: String): TimingFunction? {
        // Extract content between parentheses
        val content = value.substringAfter("cubic-bezier(").substringBefore(")")
        val parts = content.split(Regex("\\s*,\\s*"))

        if (parts.size != 4) return null

        val x1 = NumberParser.parse(parts[0])?.value ?: return null
        val y1 = NumberParser.parse(parts[1])?.value ?: return null
        val x2 = NumberParser.parse(parts[2])?.value ?: return null
        val y2 = NumberParser.parse(parts[3])?.value ?: return null

        return TimingFunction.fromCubicBezier(x1, y1, x2, y2)
    }

    private fun parseSteps(value: String): TimingFunction? {
        // Extract content between parentheses
        val content = value.substringAfter("steps(").substringBefore(")")
        val parts = content.split(Regex("\\s*,\\s*"))

        if (parts.isEmpty()) return null

        val count = NumberParser.parseInt(parts[0]) ?: return null

        // Parse optional position parameter
        val position = if (parts.size >= 2) {
            when (parts[1].trim().lowercase()) {
                "jump-start" -> TimingFunction.StepPosition.JUMP_START
                "jump-end" -> TimingFunction.StepPosition.JUMP_END
                "jump-none" -> TimingFunction.StepPosition.JUMP_NONE
                "jump-both" -> TimingFunction.StepPosition.JUMP_BOTH
                "start" -> TimingFunction.StepPosition.START
                "end" -> TimingFunction.StepPosition.END
                else -> null
            }
        } else {
            null
        }

        return TimingFunction.fromSteps(count, position)
    }
}
