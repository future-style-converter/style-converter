package app.parsing.css.properties.primitiveParsers

/**
 * Utility for detecting CSS expression functions in property values.
 *
 * CSS expressions are runtime-evaluated functions that cannot be normalized
 * at parse time. When detected, properties should store the raw expression
 * and set normalized values to null.
 *
 * Supported functions:
 * - calc() - Mathematical calculations
 * - var() - CSS custom properties (variables)
 * - clamp() - Clamped value between min and max
 * - min() - Minimum of multiple values
 * - max() - Maximum of multiple values
 * - env() - Environment variables (safe area insets, etc.)
 * - attr() - Attribute value references
 */
object ExpressionDetector {

    /**
     * All CSS expression function prefixes.
     */
    private val EXPRESSION_FUNCTIONS = listOf(
        "calc(",
        "var(",
        "clamp(",
        "min(",
        "max(",
        "env(",
        "attr("
    )

    /**
     * Check if value contains any CSS expression function.
     *
     * Use this when expressions can appear anywhere in the value,
     * e.g., "10px + var(--spacing)" or "calc(100% - 20px)".
     *
     * @param value The CSS value to check (case-insensitive)
     * @return true if value contains an expression function
     */
    fun containsExpression(value: String): Boolean {
        val lower = value.lowercase()
        return EXPRESSION_FUNCTIONS.any { lower.contains(it) }
    }

    /**
     * Check if value starts with a CSS expression function.
     *
     * Use this when the entire value must be an expression,
     * e.g., "var(--color)" or "calc(100vh - 60px)".
     *
     * @param value The CSS value to check (case-insensitive)
     * @return true if value starts with an expression function
     */
    fun startsWithExpression(value: String): Boolean {
        val lower = value.lowercase().trim()
        return EXPRESSION_FUNCTIONS.any { lower.startsWith(it) }
    }

    /**
     * Check if value is purely an expression (starts with function and is balanced).
     *
     * Alias for [startsWithExpression] for semantic clarity.
     */
    fun isExpression(value: String): Boolean = startsWithExpression(value)

    /**
     * Extract the function name if value starts with an expression.
     *
     * @param value The CSS value to check
     * @return The function name (e.g., "calc", "var") or null if not an expression
     */
    fun getExpressionType(value: String): String? {
        val lower = value.lowercase().trim()
        return EXPRESSION_FUNCTIONS
            .find { lower.startsWith(it) }
            ?.dropLast(1) // Remove the "("
    }
}
