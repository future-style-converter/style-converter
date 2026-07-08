package com.styleconverter.test.style.core.expressions

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.variables.CssVariableResolver
import com.styleconverter.test.style.core.variables.LocalCssVariables
import kotlin.math.max
import kotlin.math.min

/**
 * Evaluates CSS calc(), min(), max(), and clamp() expressions at runtime.
 *
 * ## Supported Features
 * - Basic arithmetic: +, -, *, /
 * - Nested calc(): calc(calc(100% - 20px) / 2)
 * - min()/max()/clamp() functions
 * - Mixed units with runtime resolution
 * - CSS variables within expressions
 *
 * ## Unit Handling
 * - Absolute units (px, pt, cm, etc.) → converted to pixels
 * - Percentage → resolved against container dimension (must be provided)
 * - Viewport units (vw, vh) → resolved against screen dimensions
 * - em/rem → approximated as 16px base (configurable)
 *
 * ## Example
 * ```kotlin
 * val result = CalcExpressionEvaluator.evaluate("calc(100% - 20px)", containerWidth = 300f)
 * // result = 280.0 (300 * 1.0 - 20)
 * ```
 */
object CalcExpressionEvaluator {

    private val CALC_PATTERN = Regex("""calc\s*\((.+)\)""", RegexOption.IGNORE_CASE)
    private val MIN_PATTERN = Regex("""min\s*\((.+)\)""", RegexOption.IGNORE_CASE)
    private val MAX_PATTERN = Regex("""max\s*\((.+)\)""", RegexOption.IGNORE_CASE)
    private val CLAMP_PATTERN = Regex("""clamp\s*\((.+)\)""", RegexOption.IGNORE_CASE)
    private val VAR_PATTERN = Regex("""var\s*\(\s*(--[a-zA-Z0-9_-]+)(?:\s*,\s*([^)]+))?\s*\)""")
    private val NUMBER_UNIT_PATTERN = Regex("""(-?\d+(?:\.\d+)?)\s*(px|dp|pt|em|rem|%|vw|vh|vmin|vmax|cm|mm|in|pc|Q)?""")

    /**
     * Context for expression evaluation with container dimensions.
     */
    data class EvalContext(
        val containerWidth: Float? = null,
        val containerHeight: Float? = null,
        val viewportWidth: Float = 0f,
        val viewportHeight: Float = 0f,
        val baseFontSize: Float = 16f,
        val rootFontSize: Float = 16f,
        val variables: Map<String, String> = emptyMap()
    )

    /**
     * Check if a string contains a calc(), min(), max(), or clamp() expression.
     */
    fun hasExpression(value: String?): Boolean {
        if (value == null) return false
        val lower = value.lowercase()
        return lower.contains("calc(") ||
                lower.contains("min(") ||
                lower.contains("max(") ||
                lower.contains("clamp(")
    }

    /**
     * Evaluate a CSS expression and return the result in pixels.
     */
    fun evaluate(expression: String, context: EvalContext = EvalContext()): Double? {
        val trimmed = expression.trim()

        // Handle nested functions by processing innermost first
        return try {
            evaluateExpression(trimmed, context)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Composable version that automatically gets viewport dimensions.
     */
    @Composable
    fun evaluateDp(expression: String, containerWidth: Dp? = null, containerHeight: Dp? = null): Dp? {
        val config = LocalConfiguration.current
        val density = LocalDensity.current
        val variables = LocalCssVariables.current

        val context = EvalContext(
            containerWidth = containerWidth?.value,
            containerHeight = containerHeight?.value,
            viewportWidth = config.screenWidthDp.toFloat(),
            viewportHeight = config.screenHeightDp.toFloat(),
            variables = variables.variables
        )

        return evaluate(expression, context)?.dp
    }

    private fun evaluateExpression(expr: String, context: EvalContext): Double? {
        var working = expr.trim()

        // First, resolve any var() references
        working = resolveVariables(working, context)

        // Process nested functions from innermost to outermost
        working = processNestedFunctions(working, context)

        // At this point, we should have a simple arithmetic expression
        return evaluateArithmetic(working, context)
    }

    /**
     * Resolve CSS variables in the expression.
     */
    private fun resolveVariables(expr: String, context: EvalContext): String {
        var result = expr

        VAR_PATTERN.findAll(expr).toList().reversed().forEach { match ->
            val varName = match.groupValues[1]
            val fallback = match.groupValues.getOrNull(2)?.trim()

            val value = context.variables[varName]
                ?: fallback
                ?: "0"

            result = result.replaceRange(match.range, value)
        }

        return result
    }

    /**
     * Process nested calc/min/max/clamp functions.
     */
    private fun processNestedFunctions(expr: String, context: EvalContext): String {
        var working = expr
        var changed = true

        // Keep processing until no more functions remain
        while (changed) {
            changed = false

            // Find innermost function
            val calcMatch = findInnermostCalc(working)
            val minMatch = findInnermostMin(working)
            val maxMatch = findInnermostMax(working)
            val clampMatch = findInnermostClamp(working)

            // Process whichever is innermost (highest start index of inner content)
            val matches = listOfNotNull(
                calcMatch?.let { "calc" to it },
                minMatch?.let { "min" to it },
                maxMatch?.let { "max" to it },
                clampMatch?.let { "clamp" to it }
            )

            if (matches.isNotEmpty()) {
                // Find the innermost by checking which has deepest nesting
                val best = matches.maxByOrNull { it.second.range.first }!!
                val type = best.first
                val match = best.second

                val result = when (type) {
                    "calc" -> evaluateCalcContents(match.groupValues[1], context)
                    "min" -> evaluateMinContents(match.groupValues[1], context)
                    "max" -> evaluateMaxContents(match.groupValues[1], context)
                    "clamp" -> evaluateClampContents(match.groupValues[1], context)
                    else -> null
                }

                if (result != null) {
                    working = working.replaceRange(match.range, "${result}px")
                    changed = true
                }
            }
        }

        return working
    }

    /**
     * Find innermost calc() that doesn't contain nested functions.
     */
    private fun findInnermostCalc(expr: String): MatchResult? {
        val pattern = Regex("""calc\s*\(([^()]*)\)""", RegexOption.IGNORE_CASE)
        return pattern.find(expr)
    }

    private fun findInnermostMin(expr: String): MatchResult? {
        val pattern = Regex("""min\s*\(([^()]*)\)""", RegexOption.IGNORE_CASE)
        return pattern.find(expr)
    }

    private fun findInnermostMax(expr: String): MatchResult? {
        val pattern = Regex("""max\s*\(([^()]*)\)""", RegexOption.IGNORE_CASE)
        return pattern.find(expr)
    }

    private fun findInnermostClamp(expr: String): MatchResult? {
        val pattern = Regex("""clamp\s*\(([^()]*)\)""", RegexOption.IGNORE_CASE)
        return pattern.find(expr)
    }

    /**
     * Evaluate calc() contents (arithmetic expression).
     */
    private fun evaluateCalcContents(contents: String, context: EvalContext): Double? {
        return evaluateArithmetic(contents, context)
    }

    /**
     * Evaluate min() function with comma-separated values.
     */
    private fun evaluateMinContents(contents: String, context: EvalContext): Double? {
        val values = splitByComma(contents).mapNotNull { evaluateArithmetic(it.trim(), context) }
        return values.minOrNull()
    }

    /**
     * Evaluate max() function with comma-separated values.
     */
    private fun evaluateMaxContents(contents: String, context: EvalContext): Double? {
        val values = splitByComma(contents).mapNotNull { evaluateArithmetic(it.trim(), context) }
        return values.maxOrNull()
    }

    /**
     * Evaluate clamp(min, preferred, max) function.
     */
    private fun evaluateClampContents(contents: String, context: EvalContext): Double? {
        val parts = splitByComma(contents)
        if (parts.size != 3) return null

        val minVal = evaluateArithmetic(parts[0].trim(), context) ?: return null
        val prefVal = evaluateArithmetic(parts[1].trim(), context) ?: return null
        val maxVal = evaluateArithmetic(parts[2].trim(), context) ?: return null

        return max(minVal, min(prefVal, maxVal))
    }

    /**
     * Split by comma at the top level (not inside parentheses).
     */
    private fun splitByComma(expr: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in expr) {
            when {
                char == '(' -> {
                    depth++
                    current.append(char)
                }
                char == ')' -> {
                    depth--
                    current.append(char)
                }
                char == ',' && depth == 0 -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    /**
     * Evaluate a simple arithmetic expression with values and operators.
     */
    private fun evaluateArithmetic(expr: String, context: EvalContext): Double? {
        val tokens = tokenize(expr)
        if (tokens.isEmpty()) return null

        // Convert to RPN (Reverse Polish Notation) and evaluate
        return evaluateRPN(toRPN(tokens), context)
    }

    /**
     * Token types for expression parsing.
     */
    sealed interface Token {
        data class Value(val number: Double, val unit: String?) : Token
        data class Operator(val op: Char) : Token
        data object LeftParen : Token
        data object RightParen : Token
    }

    /**
     * Tokenize the arithmetic expression.
     */
    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val str = expr.trim()

        while (i < str.length) {
            when {
                str[i].isWhitespace() -> i++

                str[i] == '(' -> {
                    tokens.add(Token.LeftParen)
                    i++
                }

                str[i] == ')' -> {
                    tokens.add(Token.RightParen)
                    i++
                }

                str[i] in listOf('+', '*', '/') -> {
                    tokens.add(Token.Operator(str[i]))
                    i++
                }

                str[i] == '-' -> {
                    // Check if this is a negative number or subtraction
                    val isNegative = tokens.isEmpty() ||
                            tokens.last() is Token.Operator ||
                            tokens.last() is Token.LeftParen

                    if (isNegative) {
                        // Parse as negative number
                        val match = NUMBER_UNIT_PATTERN.find(str, i)
                        if (match != null && match.range.first == i) {
                            val numStr = match.groupValues[1]
                            val unit = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                            tokens.add(Token.Value(numStr.toDouble(), unit))
                            i = match.range.last + 1
                        } else {
                            i++
                        }
                    } else {
                        tokens.add(Token.Operator('-'))
                        i++
                    }
                }

                str[i].isDigit() || str[i] == '.' -> {
                    val match = NUMBER_UNIT_PATTERN.find(str, i)
                    if (match != null && match.range.first == i) {
                        val numStr = match.groupValues[1]
                        val unit = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                        tokens.add(Token.Value(numStr.toDouble(), unit))
                        i = match.range.last + 1
                    } else {
                        i++
                    }
                }

                else -> i++ // Skip unknown characters
            }
        }

        return tokens
    }

    /**
     * Convert tokens to Reverse Polish Notation using shunting-yard algorithm.
     */
    private fun toRPN(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val operators = ArrayDeque<Token>()

        fun precedence(op: Char): Int = when (op) {
            '+', '-' -> 1
            '*', '/' -> 2
            else -> 0
        }

        for (token in tokens) {
            when (token) {
                is Token.Value -> output.add(token)

                is Token.Operator -> {
                    while (operators.isNotEmpty()) {
                        val top = operators.first()
                        if (top is Token.Operator && precedence(top.op) >= precedence(token.op)) {
                            output.add(operators.removeFirst())
                        } else {
                            break
                        }
                    }
                    operators.addFirst(token)
                }

                is Token.LeftParen -> operators.addFirst(token)

                is Token.RightParen -> {
                    while (operators.isNotEmpty() && operators.first() !is Token.LeftParen) {
                        output.add(operators.removeFirst())
                    }
                    if (operators.isNotEmpty() && operators.first() is Token.LeftParen) {
                        operators.removeFirst()
                    }
                }
            }
        }

        while (operators.isNotEmpty()) {
            output.add(operators.removeFirst())
        }

        return output
    }

    /**
     * Evaluate RPN expression.
     */
    private fun evaluateRPN(tokens: List<Token>, context: EvalContext): Double? {
        val stack = ArrayDeque<Double>()

        for (token in tokens) {
            when (token) {
                is Token.Value -> {
                    val pixels = convertToPixels(token.number, token.unit, context) ?: return null
                    stack.addFirst(pixels)
                }

                is Token.Operator -> {
                    if (stack.size < 2) return null
                    val b = stack.removeFirst()
                    val a = stack.removeFirst()
                    val result = when (token.op) {
                        '+' -> a + b
                        '-' -> a - b
                        '*' -> a * b
                        '/' -> if (b != 0.0) a / b else return null
                        else -> return null
                    }
                    stack.addFirst(result)
                }

                else -> {} // Skip parens in RPN
            }
        }

        return stack.firstOrNull()
    }

    /**
     * Convert a value with unit to pixels.
     */
    private fun convertToPixels(value: Double, unit: String?, context: EvalContext): Double? {
        return when (unit?.lowercase()) {
            null, "", "px", "dp" -> value

            "pt" -> value * 1.333 // 96/72

            "pc" -> value * 16.0

            "in" -> value * 96.0

            "cm" -> value * 37.795 // 96/2.54

            "mm" -> value * 3.7795 // 96/25.4

            "q" -> value * 0.945 // 96/101.6

            "%" -> {
                // Percentage of container dimension
                val container = context.containerWidth ?: context.containerHeight ?: return null
                value * container / 100.0
            }

            "vw" -> value * context.viewportWidth / 100.0

            "vh" -> value * context.viewportHeight / 100.0

            "vmin" -> value * min(context.viewportWidth, context.viewportHeight) / 100.0

            "vmax" -> value * max(context.viewportWidth, context.viewportHeight) / 100.0

            "em" -> value * context.baseFontSize

            "rem" -> value * context.rootFontSize

            else -> value // Unknown unit, treat as pixels
        }
    }

    /**
     * Convenience method to evaluate with percentage context.
     */
    fun evaluateWithPercentage(
        expression: String,
        percentageBase: Float,
        context: EvalContext = EvalContext()
    ): Double? {
        return evaluate(
            expression,
            context.copy(containerWidth = percentageBase, containerHeight = percentageBase)
        )
    }

    /**
     * Notes on implementation limitations.
     */
    object Notes {
        const val PERCENTAGE_RESOLUTION = """
            Percentages require a container dimension to resolve.
            When containerWidth/Height is null, percentage calculations return null.
            Caller should provide the relevant dimension based on the property
            (e.g., width uses containerWidth, height uses containerHeight).
        """

        const val UNIT_MIXING = """
            CSS calc() allows mixing incompatible units (e.g., calc(100% - 20px)).
            This works because the browser knows the container dimensions.
            At runtime, we need explicit container dimensions to resolve percentages.
        """

        const val VARIABLE_RESOLUTION = """
            CSS variables within calc() are resolved using LocalCssVariables.
            Fallback values in var(--name, fallback) are supported.
        """
    }
}
