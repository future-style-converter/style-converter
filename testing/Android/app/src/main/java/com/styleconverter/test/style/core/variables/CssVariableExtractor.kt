package com.styleconverter.test.style.core.variables

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.styleconverter.test.style.core.expressions.CalcExpressionEvaluator
import com.styleconverter.test.style.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts CSS variable and expression data from IR property data.
 *
 * Detects var(), calc(), min(), max(), and clamp() expressions in property
 * values and provides methods to resolve them at runtime.
 *
 * ## Detection
 * Properties may contain expressions in:
 * - String values: `"var(--color)"`, `"calc(100% - 20px)"`
 * - Expression fields: `{"type": "expression", "expr": "var(--spacing)"}`
 * - Original fields: `{"original": "var(--value)"}`
 *
 * ## Usage
 * ```kotlin
 * val extractor = CssVariableExtractor
 * if (extractor.hasVariable(prop.data)) {
 *     val resolved = extractor.resolveColor(prop.data)
 * }
 * if (extractor.hasCalcExpression(prop.data)) {
 *     val pixels = extractor.resolveCalcToDp(prop.data, containerWidth = 300.dp)
 * }
 * ```
 */
object CssVariableExtractor {

    /**
     * Check if a property value contains any var() expressions.
     */
    fun hasVariable(data: JsonElement?): Boolean {
        if (data == null) return false

        return when (data) {
            is JsonPrimitive -> {
                data.contentOrNull?.contains("var(") == true
            }
            is JsonObject -> {
                // Check common fields for var() expressions
                data["expr"]?.jsonPrimitive?.contentOrNull?.contains("var(") == true ||
                data["expression"]?.jsonPrimitive?.contentOrNull?.contains("var(") == true ||
                data["original"]?.jsonPrimitive?.contentOrNull?.contains("var(") == true ||
                data["value"]?.jsonPrimitive?.contentOrNull?.contains("var(") == true ||
                // Check if type is "expression" and contains var()
                (data["type"]?.jsonPrimitive?.contentOrNull == "expression")
            }
            else -> false
        }
    }

    /**
     * Check if a property value contains calc(), min(), max(), or clamp() expressions.
     */
    fun hasCalcExpression(data: JsonElement?): Boolean {
        if (data == null) return false

        val exprStr = extractRawExpression(data)
        return exprStr != null && CalcExpressionEvaluator.hasExpression(exprStr)
    }

    /**
     * Check if a property has any expression (var, calc, min, max, clamp).
     */
    fun hasAnyExpression(data: JsonElement?): Boolean {
        return hasVariable(data) || hasCalcExpression(data)
    }

    /**
     * Extract the raw expression string from property data (any expression, not just var).
     */
    fun extractRawExpression(data: JsonElement?): String? {
        if (data == null) return null

        return when (data) {
            is JsonPrimitive -> data.contentOrNull
            is JsonObject -> {
                data["expr"]?.jsonPrimitive?.contentOrNull
                    ?: data["expression"]?.jsonPrimitive?.contentOrNull
                    ?: data["original"]?.jsonPrimitive?.contentOrNull
                    ?: data["value"]?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }
    }

    /**
     * Extract the raw expression string from property data (var() expressions only).
     */
    fun extractExpression(data: JsonElement?): String? {
        if (data == null) return null

        return when (data) {
            is JsonPrimitive -> {
                data.contentOrNull?.takeIf { it.contains("var(") }
            }
            is JsonObject -> {
                data["expr"]?.jsonPrimitive?.contentOrNull?.takeIf { it.contains("var(") }
                    ?: data["expression"]?.jsonPrimitive?.contentOrNull?.takeIf { it.contains("var(") }
                    ?: data["original"]?.jsonPrimitive?.contentOrNull?.takeIf { it.contains("var(") }
                    ?: data["value"]?.jsonPrimitive?.contentOrNull?.takeIf { it.contains("var(") }
            }
            else -> null
        }
    }

    /**
     * Resolve a property value containing var() to a Color.
     */
    @Composable
    fun resolveToColor(data: JsonElement?): Color? {
        val expr = extractExpression(data) ?: return null
        return CssVariableResolver.resolveToColor(expr)
    }

    /**
     * Resolve a property value containing var() to a Dp.
     */
    @Composable
    fun resolveToDp(data: JsonElement?): Dp? {
        val expr = extractExpression(data) ?: return null
        return CssVariableResolver.resolveToDp(expr)
    }

    /**
     * Resolve a property value containing var() to a TextUnit.
     */
    @Composable
    fun resolveToTextUnit(data: JsonElement?): TextUnit? {
        val expr = extractExpression(data) ?: return null
        return CssVariableResolver.resolveToTextUnit(expr)
    }

    /**
     * Resolve a property value containing var() to a Float.
     */
    @Composable
    fun resolveToFloat(data: JsonElement?): Float? {
        val expr = extractExpression(data) ?: return null
        return CssVariableResolver.resolveToFloat(expr)
    }

    /**
     * Resolve a property value containing var() to a String.
     */
    @Composable
    fun resolveToString(data: JsonElement?): String? {
        val expr = extractExpression(data) ?: return null
        return CssVariableResolver.resolveToString(expr)
    }

    // ==================== Calc Expression Resolution ====================

    /**
     * Resolve a calc() expression to Dp.
     *
     * @param data The property data containing the expression
     * @param containerWidth Container width for percentage resolution
     * @param containerHeight Container height for percentage resolution
     */
    @Composable
    fun resolveCalcToDp(
        data: JsonElement?,
        containerWidth: Dp? = null,
        containerHeight: Dp? = null
    ): Dp? {
        val expr = extractRawExpression(data) ?: return null
        return CalcExpressionEvaluator.evaluateDp(expr, containerWidth, containerHeight)
    }

    /**
     * Resolve a calc() expression to Float (for unitless values).
     *
     * @param data The property data containing the expression
     * @param containerDimension Container dimension for percentage resolution
     */
    @Composable
    fun resolveCalcToFloat(
        data: JsonElement?,
        containerDimension: Float? = null
    ): Float? {
        val expr = extractRawExpression(data) ?: return null
        val variables = LocalCssVariables.current
        val context = CalcExpressionEvaluator.EvalContext(
            containerWidth = containerDimension,
            variables = variables.variables
        )
        return CalcExpressionEvaluator.evaluate(expr, context)?.toFloat()
    }

    /**
     * Resolve any expression (var, calc, min, max, clamp) to Dp.
     * First resolves var() references, then evaluates calc expressions.
     */
    @Composable
    fun resolveAnyToDp(
        data: JsonElement?,
        containerWidth: Dp? = null,
        containerHeight: Dp? = null
    ): Dp? {
        val rawExpr = extractRawExpression(data) ?: return null

        // If it's just a var() without calc, use the simpler resolution
        if (rawExpr.contains("var(") && !CalcExpressionEvaluator.hasExpression(rawExpr)) {
            return resolveToDp(data)
        }

        // Otherwise use calc evaluator (which also handles var)
        return CalcExpressionEvaluator.evaluateDp(rawExpr, containerWidth, containerHeight)
    }

    /**
     * Resolve any expression to Float.
     */
    @Composable
    fun resolveAnyToFloat(
        data: JsonElement?,
        containerDimension: Float? = null
    ): Float? {
        val rawExpr = extractRawExpression(data) ?: return null

        // If it's just a var() without calc, use the simpler resolution
        if (rawExpr.contains("var(") && !CalcExpressionEvaluator.hasExpression(rawExpr)) {
            return resolveToFloat(data)
        }

        // Otherwise use calc evaluator
        return resolveCalcToFloat(data, containerDimension)
    }

    /**
     * Get all variable references from a list of properties.
     *
     * Returns a set of variable names (--name) that are referenced.
     */
    fun extractVariableReferences(properties: List<IRProperty>): Set<String> {
        val references = mutableSetOf<String>()
        val varPattern = Regex("""var\(\s*(--[a-zA-Z0-9_-]+)""")

        properties.forEach { prop ->
            val expr = extractExpression(prop.data)
            if (expr != null) {
                varPattern.findAll(expr).forEach { match ->
                    references.add(match.groupValues[1])
                }
            }
        }

        return references
    }

    /**
     * Check if a property list has any unresolved variable references.
     */
    fun hasUnresolvedVariables(properties: List<IRProperty>): Boolean {
        return properties.any { hasVariable(it.data) }
    }

    /**
     * Get a mapping of property types to their variable expressions.
     */
    fun getVariableProperties(properties: List<IRProperty>): Map<String, String> {
        val result = mutableMapOf<String, String>()

        properties.forEach { prop ->
            val expr = extractExpression(prop.data)
            if (expr != null) {
                result[prop.type] = expr
            }
        }

        return result
    }
}

/**
 * Composable helper to resolve a property with variable fallback.
 *
 * Tries to use the normalized value first, falls back to resolving
 * the variable expression if the normalized value is null.
 */
@Composable
inline fun <T> resolveWithVariableFallback(
    normalizedValue: T?,
    data: JsonElement?,
    crossinline resolver: @Composable (JsonElement?) -> T?
): T? {
    return normalizedValue ?: if (CssVariableExtractor.hasVariable(data)) {
        resolver(data)
    } else {
        null
    }
}
