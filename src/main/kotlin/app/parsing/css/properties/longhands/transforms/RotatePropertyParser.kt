package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRProperty
import app.irmodels.properties.transforms.RotateProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser

/**
 * Parser for the CSS `rotate` property.
 *
 * Syntax: none | <angle> | [ x | y | z | <number>{3} ] && <angle>
 *
 * Examples:
 * - "none" → Rotation.None
 * - "45deg" → Rotation.Angle(IRAngle(45.0, DEG))
 * - "90deg" → Rotation.Angle(IRAngle(90.0, DEG))
 * - "1 0 0 45deg" → Rotation.AxisAngle(x=1, y=0, z=0, angle=45deg)
 * - "x 45deg" → Rotation.AxisAngle(x=1, y=0, z=0, angle=45deg)
 * - "y 90deg" → Rotation.AxisAngle(x=0, y=1, z=0, angle=90deg)
 * - "z 180deg" → Rotation.AxisAngle(x=0, y=0, z=1, angle=180deg)
 */
object RotatePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'none' keyword
        if (trimmed == "none") {
            return RotateProperty(RotateProperty.Rotation.None())
        }

        // Split into parts
        val parts = trimmed.split(Regex("\\s+"))

        // Case 1: Just an angle (e.g., "45deg")
        if (parts.size == 1) {
            val angle = AngleParser.parse(parts[0]) ?: return null
            return RotateProperty(RotateProperty.Rotation.Angle(angle))
        }

        // Case 2: Axis and angle (e.g., "x 45deg" or "1 0 0 45deg")
        if (parts.size == 2) {
            // Parse the angle (last part)
            val angle = AngleParser.parse(parts[1]) ?: return null

            // Parse the axis (first part)
            val (x, y, z) = when (parts[0]) {
                "x" -> Triple(1.0, 0.0, 0.0)
                "y" -> Triple(0.0, 1.0, 0.0)
                "z" -> Triple(0.0, 0.0, 1.0)
                else -> return null
            }

            return RotateProperty(RotateProperty.Rotation.AxisAngle(x, y, z, angle))
        }

        // Case 3: Three numbers and angle (e.g., "1 0 0 45deg")
        if (parts.size == 4) {
            val x = parts[0].toDoubleOrNull() ?: return null
            val y = parts[1].toDoubleOrNull() ?: return null
            val z = parts[2].toDoubleOrNull() ?: return null
            val angle = AngleParser.parse(parts[3]) ?: return null

            return RotateProperty(RotateProperty.Rotation.AxisAngle(x, y, z, angle))
        }

        return null
    }
}
