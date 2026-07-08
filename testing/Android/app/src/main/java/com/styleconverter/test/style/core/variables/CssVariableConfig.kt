package com.styleconverter.test.style.core.variables

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

/**
 * Common theme variable presets.
 */
object ThemeVariables {
    /**
     * Material Design 3 inspired color variables.
     */
    val materialLight = CssVariableScope(
        mapOf(
            "--primary" to "#6750A4",
            "--on-primary" to "#FFFFFF",
            "--primary-container" to "#EADDFF",
            "--on-primary-container" to "#21005D",
            "--secondary" to "#625B71",
            "--on-secondary" to "#FFFFFF",
            "--secondary-container" to "#E8DEF8",
            "--on-secondary-container" to "#1D192B",
            "--tertiary" to "#7D5260",
            "--on-tertiary" to "#FFFFFF",
            "--error" to "#B3261E",
            "--on-error" to "#FFFFFF",
            "--background" to "#FFFBFE",
            "--on-background" to "#1C1B1F",
            "--surface" to "#FFFBFE",
            "--on-surface" to "#1C1B1F",
            "--outline" to "#79747E"
        )
    )

    val materialDark = CssVariableScope(
        mapOf(
            "--primary" to "#D0BCFF",
            "--on-primary" to "#381E72",
            "--primary-container" to "#4F378B",
            "--on-primary-container" to "#EADDFF",
            "--secondary" to "#CCC2DC",
            "--on-secondary" to "#332D41",
            "--secondary-container" to "#4A4458",
            "--on-secondary-container" to "#E8DEF8",
            "--tertiary" to "#EFB8C8",
            "--on-tertiary" to "#492532",
            "--error" to "#F2B8B5",
            "--on-error" to "#601410",
            "--background" to "#1C1B1F",
            "--on-background" to "#E6E1E5",
            "--surface" to "#1C1B1F",
            "--on-surface" to "#E6E1E5",
            "--outline" to "#938F99"
        )
    )

    /**
     * Common spacing variables.
     */
    val spacing = CssVariableScope(
        mapOf(
            "--spacing-xs" to "4px",
            "--spacing-sm" to "8px",
            "--spacing-md" to "16px",
            "--spacing-lg" to "24px",
            "--spacing-xl" to "32px",
            "--spacing-xxl" to "48px"
        )
    )

    /**
     * Common typography variables.
     */
    val typography = CssVariableScope(
        mapOf(
            "--font-size-xs" to "10px",
            "--font-size-sm" to "12px",
            "--font-size-md" to "14px",
            "--font-size-lg" to "18px",
            "--font-size-xl" to "24px",
            "--font-size-xxl" to "32px",
            "--line-height-tight" to "1.25",
            "--line-height-normal" to "1.5",
            "--line-height-relaxed" to "1.75"
        )
    )
}
