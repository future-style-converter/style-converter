package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRNumber

/**
 * Parses CSS numeric values (unitless numbers) into IRNumber instances.
 *
 * Used for properties like:
 * - font-weight: 600 (numeric)
 * - opacity: 0.5
 * - line-height: 1.5
 * - z-index: 10
 *
 * Examples:
 * - "1.5" → IRNumber(1.5)
 * - "600" → IRNumber(600.0)
 * - "0.9" → IRNumber(0.9)
 */
object NumberParser {

    private val numberRegex = """^[+-]?\d*\.?\d+$""".toRegex()

    /**
     * Parse a numeric value.
     *
     * @param value The number string (e.g., "1.5", "600", "0.9")
     * @return IRNumber instance, or null if parsing fails
     */
    fun parse(value: String): IRNumber? {
        val trimmed = value.trim()

        // Check if it matches number pattern
        if (!numberRegex.matches(trimmed)) {
            return null
        }

        val numValue = trimmed.toDoubleOrNull() ?: return null
        return IRNumber(numValue)
    }

    /**
     * Parse a number as an integer.
     *
     * @param value The number string
     * @return Integer value, or null if parsing fails
     */
    fun parseInt(value: String): Int? {
        val trimmed = value.trim()
        return trimmed.toIntOrNull()
    }

    /**
     * Parse a number as a double.
     *
     * @param value The number string
     * @return Double value, or null if parsing fails
     */
    fun parseDouble(value: String): Double? {
        val trimmed = value.trim()
        return trimmed.toDoubleOrNull()
    }
}
