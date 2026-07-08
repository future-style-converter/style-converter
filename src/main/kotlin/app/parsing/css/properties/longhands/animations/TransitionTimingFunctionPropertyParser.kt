package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.TimingFunction
import app.irmodels.properties.animations.TransitionTimingFunctionProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser

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
            return parseCubicBezier(trimmed)
        }

        // Handle steps(count, position?)
        if (trimmed.startsWith("steps(") && trimmed.endsWith(")")) {
            return parseSteps(trimmed)
        }

        return null
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
