package com.styleconverter.runtime.core.variables

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS custom properties (variables).
 *
 * CSS variables provide a way to define reusable values that can be
 * referenced throughout a stylesheet. In SDUI, these are resolved at runtime.
 *
 * ## Syntax
 * - Definition: `--primary-color: #3498db`
 * - Usage: `var(--primary-color)` or `var(--primary-color, fallback)`
 *
 * ## Example
 * ```kotlin
 * val variables = CssVariableScope(
 *     mapOf(
 *         "--primary-color" to "#3498db",
 *         "--spacing-unit" to "8px",
 *         "--font-size-large" to "24px"
 *     )
 * )
 * ```
 */
data class CssVariableScope(
    val variables: Map<String, String> = emptyMap()
) {
    /**
     * Get a variable value, optionally with a fallback.
     */
    fun get(name: String, fallback: String? = null): String? {
        return variables[name] ?: fallback
    }

    /**
     * Check if a variable is defined.
     */
    fun contains(name: String): Boolean = variables.containsKey(name)

    /**
     * Merge with another scope (child overrides parent).
     */
    fun merge(child: CssVariableScope): CssVariableScope {
        return CssVariableScope(variables + child.variables)
    }

    companion object {
        val EMPTY = CssVariableScope()
    }
}

/**
 * CompositionLocal for CSS variable scope.
 *
 * Allows components to access and provide CSS variables to their children.
 */
val LocalCssVariables = compositionLocalOf { CssVariableScope.EMPTY }

/**
 * Represents a parsed CSS var() expression.
 *
 * @param variableName The variable name (e.g., "--primary-color")
 * @param fallback Optional fallback value if variable is undefined
 */
data class VarExpression(
    val variableName: String,
    val fallback: String? = null
) {
    companion object {
        private val VAR_PATTERN = Regex("""var\(\s*(--[a-zA-Z0-9_-]+)\s*(?:,\s*(.+))?\s*\)""")

        /**
         * Parse a var() expression from a string.
         *
         * @param value The CSS value that may contain var() expressions
         * @return The parsed VarExpression, or null if not a var() expression
         */
        fun parse(value: String): VarExpression? {
            val match = VAR_PATTERN.find(value.trim()) ?: return null
            return VarExpression(
                variableName = match.groupValues[1],
                fallback = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
            )
        }

        /**
         * Check if a value contains a var() expression.
         */
        fun containsVar(value: String): Boolean {
            return value.contains("var(")
        }
    }
}

/**
 * Parsed variable definition.
 */
data class VariableDefinition(
    val name: String,
    val value: String
) {
    companion object {
        private val DEFINITION_PATTERN = Regex("""^(--[a-zA-Z0-9_-]+)\s*:\s*(.+)$""")

        /**
         * Parse a CSS variable definition.
         */
        fun parse(property: String): VariableDefinition? {
            val match = DEFINITION_PATTERN.find(property.trim()) ?: return null
            return VariableDefinition(
                name = match.groupValues[1],
                value = match.groupValues[2].trim()
            )
        }
    }
}
