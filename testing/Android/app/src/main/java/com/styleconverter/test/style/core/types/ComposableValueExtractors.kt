package com.styleconverter.test.style.core.types

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styleconverter.test.style.core.expressions.CalcExpressionEvaluator
import com.styleconverter.test.style.core.variables.CssVariableExtractor
import com.styleconverter.test.style.core.variables.CssVariableResolver
import com.styleconverter.test.style.core.variables.LocalCssVariables
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Composable value extractors that resolve CSS variables and calc() expressions at runtime.
 *
 * These extractors check for:
 * 1. var() expressions - resolved using [LocalCssVariables] scope
 * 2. calc()/min()/max()/clamp() expressions - evaluated mathematically
 * 3. Standard normalized values - extracted directly
 *
 * ## Usage
 * ```kotlin
 * @Composable
 * fun MyComponent(data: JsonElement?) {
 *     val color = ComposableValueExtractors.extractColor(data)
 *     // color will be resolved even if data contains var(--my-color)
 *
 *     val width = ComposableValueExtractors.extractDpWithCalc(data, containerWidth = 300.dp)
 *     // width will be evaluated even if data contains calc(100% - 20px)
 * }
 * ```
 */
object ComposableValueExtractors {

    /**
     * Extract a Dp value, resolving CSS variables if present.
     */
    @Composable
    fun extractDp(json: JsonElement?): Dp? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToDp(json)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractDp(json)
    }

    /**
     * Extract a Dp value, supporting calc(), min(), max(), clamp() expressions.
     *
     * @param json The property data
     * @param containerWidth Container width for percentage calculations (e.g., for width property)
     * @param containerHeight Container height for percentage calculations (e.g., for height property)
     */
    @Composable
    fun extractDpWithCalc(
        json: JsonElement?,
        containerWidth: Dp? = null,
        containerHeight: Dp? = null
    ): Dp? {
        if (json == null) return null

        // Check for calc/min/max/clamp expressions first
        if (CssVariableExtractor.hasCalcExpression(json)) {
            return CssVariableExtractor.resolveCalcToDp(json, containerWidth, containerHeight)
        }

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToDp(json)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractDp(json)
    }

    /**
     * Extract any expression (var, calc, etc.) to Dp with full support.
     * Use this when the value could be any CSS expression.
     */
    @Composable
    fun extractAnyDp(
        json: JsonElement?,
        containerWidth: Dp? = null,
        containerHeight: Dp? = null
    ): Dp? {
        if (json == null) return null

        // Check for any expression (var, calc, min, max, clamp)
        if (CssVariableExtractor.hasAnyExpression(json)) {
            return CssVariableExtractor.resolveAnyToDp(json, containerWidth, containerHeight)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractDp(json)
    }

    /**
     * Extract a Color, resolving CSS variables if present.
     */
    @Composable
    fun extractColor(json: JsonElement?): Color? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToColor(json)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractColor(json)
    }

    /**
     * Extract a Float, resolving CSS variables if present.
     */
    @Composable
    fun extractFloat(json: JsonElement?): Float? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToFloat(json)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractFloat(json)
    }

    /**
     * Extract a Float, supporting calc() expressions.
     *
     * @param json The property data
     * @param containerDimension Container dimension for percentage calculations
     */
    @Composable
    fun extractFloatWithCalc(
        json: JsonElement?,
        containerDimension: Float? = null
    ): Float? {
        if (json == null) return null

        // Check for calc expressions first
        if (CssVariableExtractor.hasCalcExpression(json)) {
            return CssVariableExtractor.resolveCalcToFloat(json, containerDimension)
        }

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToFloat(json)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractFloat(json)
    }

    /**
     * Extract any expression (var, calc, etc.) to Float with full support.
     */
    @Composable
    fun extractAnyFloat(
        json: JsonElement?,
        containerDimension: Float? = null
    ): Float? {
        if (json == null) return null

        if (CssVariableExtractor.hasAnyExpression(json)) {
            return CssVariableExtractor.resolveAnyToFloat(json, containerDimension)
        }

        return ValueExtractors.extractFloat(json)
    }

    /**
     * Extract a TextUnit (sp), resolving CSS variables if present.
     */
    @Composable
    fun extractTextUnit(json: JsonElement?): TextUnit? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToTextUnit(json)
        }

        // Fall back to Dp extraction and convert to sp
        return ValueExtractors.extractDp(json)?.value?.sp
    }

    /**
     * Extract a String keyword, resolving CSS variables if present.
     */
    @Composable
    fun extractKeyword(json: JsonElement?): String? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToString(json)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractKeyword(json)
    }

    /**
     * Extract an Int, resolving CSS variables if present.
     */
    @Composable
    fun extractInt(json: JsonElement?): Int? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToFloat(json)?.toInt()
        }

        // Fall back to standard extraction
        return ValueExtractors.extractInt(json)
    }

    /**
     * Extract a percentage (0-100), resolving CSS variables if present.
     */
    @Composable
    fun extractPercentage(json: JsonElement?): Float? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToFloat(json)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractPercentage(json)
    }

    /**
     * Extract a percentage (0-100), supporting calc() expressions.
     *
     * Note: Since percentages in calc() are relative to a container,
     * the result depends on context. For raw percentage extraction,
     * the result is the evaluated percentage value.
     */
    @Composable
    fun extractPercentageWithCalc(
        json: JsonElement?,
        containerDimension: Float? = null
    ): Float? {
        if (json == null) return null

        // Check for calc expressions
        if (CssVariableExtractor.hasCalcExpression(json)) {
            // Evaluate and return as percentage of container
            val pixels = CssVariableExtractor.resolveCalcToFloat(json, containerDimension)
            if (pixels != null && containerDimension != null && containerDimension > 0) {
                return (pixels / containerDimension) * 100f
            }
            return pixels
        }

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            return CssVariableExtractor.resolveToFloat(json)
        }

        return ValueExtractors.extractPercentage(json)
    }

    /**
     * Extract border width, resolving CSS variables if present.
     */
    @Composable
    fun extractBorderWidth(json: JsonElement?): Dp? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            val resolved = CssVariableExtractor.resolveToString(json)
            return resolved?.let { parseBorderWidth(it) }
        }

        // Fall back to standard extraction
        return ValueExtractors.extractBorderWidth(json)
    }

    /**
     * Parse border width from resolved string.
     */
    private fun parseBorderWidth(value: String): Dp? {
        return when (value.lowercase().trim()) {
            "thin" -> 1.dp
            "medium" -> 3.dp
            "thick" -> 5.dp
            else -> {
                // Try to parse as length
                val match = Regex("""(-?\d+(?:\.\d+)?)\s*(px|dp)?""").find(value)
                match?.groupValues?.get(1)?.toFloatOrNull()?.dp
            }
        }
    }

    /**
     * Extract degrees from angle, resolving CSS variables if present.
     */
    @Composable
    fun extractDegrees(json: JsonElement?): Float? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            val resolved = CssVariableExtractor.resolveToString(json)
            return resolved?.let { parseAngle(it) }
        }

        // Fall back to standard extraction
        return ValueExtractors.extractDegrees(json)
    }

    /**
     * Parse angle from resolved string.
     */
    private fun parseAngle(value: String): Float? {
        val match = Regex("""(-?\d+(?:\.\d+)?)\s*(deg|rad|grad|turn)?""").find(value.trim())
        if (match != null) {
            val number = match.groupValues[1].toFloatOrNull() ?: return null
            val unit = match.groupValues.getOrNull(2) ?: "deg"
            return when (unit.lowercase()) {
                "deg" -> number
                "rad" -> number * (180f / Math.PI.toFloat())
                "grad" -> number * 0.9f
                "turn" -> number * 360f
                else -> number
            }
        }
        return null
    }

    /**
     * Extract milliseconds from time, resolving CSS variables if present.
     */
    @Composable
    fun extractMillis(json: JsonElement?): Int? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            val resolved = CssVariableExtractor.resolveToString(json)
            return resolved?.let { parseTime(it) }
        }

        // Fall back to standard extraction
        return ValueExtractors.extractMillis(json)
    }

    /**
     * Parse time from resolved string.
     */
    private fun parseTime(value: String): Int? {
        val match = Regex("""(-?\d+(?:\.\d+)?)\s*(ms|s)?""").find(value.trim())
        if (match != null) {
            val number = match.groupValues[1].toFloatOrNull() ?: return null
            val unit = match.groupValues.getOrNull(2) ?: "ms"
            return when (unit.lowercase()) {
                "s" -> (number * 1000).toInt()
                "ms" -> number.toInt()
                else -> number.toInt()
            }
        }
        return null
    }

    /**
     * Extract font weight, resolving CSS variables if present.
     */
    @Composable
    fun extractFontWeight(json: JsonElement?): Int? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            val resolved = CssVariableExtractor.resolveToString(json)
            return resolved?.let { parseFontWeight(it) }
        }

        // Fall back to standard extraction
        return ValueExtractors.extractFontWeight(json)
    }

    /**
     * Parse font weight from resolved string.
     */
    private fun parseFontWeight(value: String): Int? {
        return value.trim().toIntOrNull() ?: when (value.lowercase().trim()) {
            "normal" -> 400
            "bold" -> 700
            "lighter" -> null
            "bolder" -> null
            else -> null
        }
    }

    /**
     * Extract line style, resolving CSS variables if present.
     */
    @Composable
    fun extractLineStyle(json: JsonElement?): ValueExtractors.LineStyle? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            val resolved = CssVariableExtractor.resolveToString(json)
            return resolved?.let { parseLineStyle(it) }
        }

        // Fall back to standard extraction
        return ValueExtractors.extractLineStyle(json)
    }

    /**
     * Parse line style from resolved string.
     */
    private fun parseLineStyle(value: String): ValueExtractors.LineStyle? {
        return try {
            ValueExtractors.LineStyle.valueOf(value.uppercase().trim())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extract length or percentage, resolving CSS variables if present.
     */
    @Composable
    fun extractLengthOrPercentage(json: JsonElement?): ValueExtractors.LengthOrPercentage? {
        if (json == null) return null

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            val resolved = CssVariableExtractor.resolveToString(json) ?: return null
            return parseLengthOrPercentage(resolved)
        }

        // Fall back to standard extraction
        return ValueExtractors.extractLengthOrPercentage(json)
    }

    /**
     * Extract length or percentage, supporting calc() expressions.
     *
     * When calc() is evaluated, the result is always a Length since
     * we're computing the absolute value. The containerDimension is
     * used to resolve any percentage values within the expression.
     *
     * @param json The property data
     * @param containerDimension Container dimension for resolving percentages
     */
    @Composable
    fun extractLengthOrPercentageWithCalc(
        json: JsonElement?,
        containerDimension: Dp? = null
    ): ValueExtractors.LengthOrPercentage? {
        if (json == null) return null

        // Check for calc expressions first
        if (CssVariableExtractor.hasCalcExpression(json)) {
            val result = CssVariableExtractor.resolveCalcToDp(
                json,
                containerWidth = containerDimension,
                containerHeight = containerDimension
            )
            return result?.let { ValueExtractors.LengthOrPercentage.Length(it) }
        }

        // Check for var() expression
        if (CssVariableExtractor.hasVariable(json)) {
            val resolved = CssVariableExtractor.resolveToString(json) ?: return null
            return parseLengthOrPercentage(resolved)
        }

        return ValueExtractors.extractLengthOrPercentage(json)
    }

    /**
     * Parse length or percentage from resolved string.
     */
    private fun parseLengthOrPercentage(value: String): ValueExtractors.LengthOrPercentage? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "auto") {
            return ValueExtractors.LengthOrPercentage.Auto
        }

        // Check for percentage
        if (trimmed.endsWith("%")) {
            val number = trimmed.dropLast(1).toFloatOrNull()
            if (number != null) {
                return ValueExtractors.LengthOrPercentage.Percentage(number / 100f)
            }
        }

        // Try to parse as length
        val match = Regex("""(-?\d+(?:\.\d+)?)\s*(px|dp|em|rem)?""").find(trimmed)
        if (match != null) {
            val number = match.groupValues[1].toFloatOrNull() ?: return null
            return ValueExtractors.LengthOrPercentage.Length(number.dp)
        }

        return null
    }

    /**
     * Extract shadows, resolving CSS variables in individual shadow properties.
     */
    @Composable
    fun extractShadows(json: JsonElement?): List<ValueExtractors.ShadowData> {
        // For now, delegate to standard extraction
        // Full shadow variable resolution would require more complex parsing
        return ValueExtractors.extractShadows(json)
    }
}

/**
 * Extension function to check if extraction should use composable path.
 */
fun JsonElement?.hasVariables(): Boolean {
    return this != null && CssVariableExtractor.hasVariable(this)
}
