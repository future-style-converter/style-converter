package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRPercentage

/**
 * Parses CSS percentage values into IRPercentage instances.
 *
 * Percentages are relative to a reference value (usually parent element's dimension).
 *
 * Examples:
 * - "50%" → IRPercentage(50.0)
 * - "100%" → IRPercentage(100.0)
 * - "150%" → IRPercentage(150.0)
 */
object PercentageParser {

    private val percentageRegex = """^([+-]?\d*\.?\d+)%$""".toRegex()

    /**
     * Parse a CSS percentage value.
     *
     * @param value The percentage string (e.g., "50%", "100%")
     * @return IRPercentage instance, or null if parsing fails
     */
    fun parse(value: String): IRPercentage? {
        val trimmed = value.trim()

        // Match against percentage pattern
        val match = percentageRegex.find(trimmed) ?: return null

        val numStr = match.groupValues[1]
        val numValue = numStr.toDoubleOrNull() ?: return null

        return IRPercentage(numValue)
    }
}
