package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRTime

/**
 * Parses CSS time values into IRTime instances with normalized milliseconds.
 *
 * Supports:
 * - Seconds: s → normalized to milliseconds
 * - Milliseconds: ms
 *
 * Examples:
 * - "0.3s" → IRTime(ms=300.0, original=0.3, unit=S)
 * - "300ms" → IRTime(ms=300.0, original=300.0, unit=MS)
 * - "2s" → IRTime(ms=2000.0, original=2.0, unit=S)
 */
object TimeParser {

    private val timeRegex = """^([+-]?\d*\.?\d+)(s|ms)$""".toRegex()

    /**
     * Parse a CSS time value, normalizing to milliseconds.
     *
     * @param value The time string (e.g., "0.3s", "300ms", "0s")
     * @return IRTime instance with normalized milliseconds, or null if parsing fails
     */
    fun parse(value: String): IRTime? {
        val trimmed = value.trim()

        // Match against time pattern
        val match = timeRegex.find(trimmed) ?: return null

        val (numStr, unitStr) = match.destructured
        val numValue = numStr.toDoubleOrNull() ?: return null

        // Use factory methods that normalize to milliseconds
        return when (unitStr.lowercase()) {
            "s" -> IRTime.fromSeconds(numValue)
            "ms" -> IRTime.fromMilliseconds(numValue)
            else -> null
        }
    }

    /**
     * Try to parse as time, returning a default if it fails.
     */
    fun parseOrDefault(value: String, default: IRTime): IRTime {
        return parse(value) ?: default
    }
}
