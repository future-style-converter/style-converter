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
 * - "4.5e1deg" → degrees=45 (exponent form, wave 52)
 * - "45DEG" → degrees=45 (units are ASCII case-insensitive, wave 52)
 */
object AngleParser {

    // The <number> part follows css-values-4 §5.3 (a sign, digits with an
    // optional fraction — a digit is required after the `.`, so `1.deg` stays
    // invalid — or a bare `.5`, then an OPTIONAL e/E exponent);
    // css-syntax-3 §4.3.13 ("Consume a number") folds that exponent into the
    // <number-token> BEFORE §4.3.3 attaches the unit, so `4.5e1deg` is ONE
    // <dimension-token> with the ordinary unit `deg`. The unit alternation
    // matches case-insensitively because CSS dimension units, like every CSS
    // keyword, are ASCII case-insensitive (css-values-4); the `when` below
    // already lowercased the unit, but before wave 52 `45DEG` never reached
    // it. Pre-wave-52 shape `^([+-]?\d*\.?\d+)(deg|rad|grad|turn)$` read
    // neither, and the null it returned cost a SILENT WRONG COLOUR in
    // `ColorParser.parseHslHue` (`hsl(1.2e2deg, …)` fell back to hue 0, red)
    // and a Raw / Generic passthrough in every gradient, transform, rotate,
    // filter and image-orientation consumer (BACKLOG 0(e), wave-52 lane L5 F4).
    private val angleRegex =
        """^([+-]?(?:\d+(?:\.\d+)?|\.\d+)(?:[eE][+-]?\d+)?)(deg|rad|grad|turn)$"""
            .toRegex(RegexOption.IGNORE_CASE)

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
