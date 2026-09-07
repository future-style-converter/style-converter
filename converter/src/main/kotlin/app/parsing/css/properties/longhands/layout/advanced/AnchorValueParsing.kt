package app.parsing.css.properties.longhands.layout.advanced

// Shared <position>-for-offset reader. Same package as both callers.
import app.irmodels.IRPercentage
import app.irmodels.properties.layout.advanced.AnchorValue
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * Reads one component of the motion-path `offset-anchor` / `offset-position`
 * value into the IR [AnchorValue] variants: the `auto` keyword, the four
 * physical position keywords plus `center`, a `<percentage>`, or a `<length>`.
 *
 * A6#14: OffsetAnchorPropertyParser and OffsetPositionPropertyParser each
 * carried a byte-identical private `parseAnchorValue`. motion-1 defines both
 * properties over the SAME `<position>` production, so one body is the
 * correct shape; two copies is how a fix to one silently misses the other.
 * Moved verbatim — the differential over every fixture is byte-identical.
 */
internal object AnchorValueParsing {

    /** @param s one already-trimmed component of the value */
    fun parseAnchorValue(s: String): AnchorValue? {
        return when (s) {
            "auto" -> AnchorValue.Auto
            "center" -> AnchorValue.Center
            "left" -> AnchorValue.Left
            "right" -> AnchorValue.Right
            "top" -> AnchorValue.Top
            "bottom" -> AnchorValue.Bottom
            else -> {
                if (s.endsWith("%")) {
                    val percent = s.removeSuffix("%").toDoubleOrNull() ?: return null
                    AnchorValue.Percentage(IRPercentage(percent))
                } else {
                    val length = LengthParser.parse(s) ?: return null
                    AnchorValue.Length(length)
                }
            }
        }
    }
}
