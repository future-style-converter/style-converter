package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRAngle

/**
 * Parses CSS angle values into IRAngle instances.
 *
 * All angles are normalized to degrees during parsing for cross-platform use.
 * Original value and unit are preserved for CSS regeneration.
 *
 * Supports:
 * - deg: Degrees (360deg = full circle)
 * - rad: Radians (2π rad = full circle)
 * - grad: Gradians (400grad = full circle)
 * - turn: Turns (1turn = full circle)
 *
 * Examples:
 * - "45deg" → degrees=45, original=45deg
 * - "1.57rad" → degrees≈90, original=1.57rad
 * - "0.5turn" → degrees=180, original=0.5turn
 */
object AngleParser {

    private val angleRegex = """^([+-]?\d*\.?\d+)(deg|rad|grad|turn)$""".toRegex()

    /**
     * Parse a CSS angle value and normalize to degrees.
     *
     * @param value The angle string (e.g., "45deg", "1.57rad", "0.5turn")
     * @return IRAngle with normalized degrees, or null if parsing fails
     */
    fun parse(value: String): IRAngle? {
        val trimmed = value.trim()

        // Special case: unitless zero is valid for angles
        if (trimmed == "0" || trimmed == "0.0") {
            return IRAngle.fromDegrees(0.0)
        }

        // Match against angle pattern
        val match = angleRegex.find(trimmed) ?: return null

        val (numStr, unitStr) = match.destructured
        val numValue = numStr.toDoubleOrNull() ?: return null

        // Parse and normalize to degrees using IRAngle factory methods
        return when (unitStr.lowercase()) {
            "deg" -> IRAngle.fromDegrees(numValue)
            "rad" -> IRAngle.fromRadians(numValue)
            "grad" -> IRAngle.fromGradians(numValue)
            "turn" -> IRAngle.fromTurns(numValue)
            else -> null
        }
    }
}
