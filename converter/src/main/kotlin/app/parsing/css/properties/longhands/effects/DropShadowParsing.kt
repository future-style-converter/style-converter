package app.parsing.css.properties.longhands.effects

// Shared drop-shadow() argument reader. Same package as both callers.
import app.irmodels.properties.color.FilterFunction
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * Reads the arguments of filter-effects-1 `drop-shadow(<length>{2,3}
 * <color>?)` into [FilterFunction.DropShadow].
 *
 * A6#14: FilterPropertyParser and BackdropFilterPropertyParser each carried a
 * byte-identical private `parseDropShadow`. filter-effects-1 gives `filter`
 * and `backdrop-filter` the SAME `<filter-function-list>` grammar, so the two
 * readers must agree by construction; keeping two bodies is precisely the
 * shape that let the wave-47 border defect (fix one, miss the sibling) exist.
 * Moved verbatim — the differential over every fixture is byte-identical, so
 * this touches no rendering, including the ring-fenced
 * filter-effects/backdrop-filter-basic-blur cell.
 */
internal object DropShadowParsing {

    /** @param args the text between `drop-shadow(` and its closing paren */
    fun parseDropShadow(args: String): FilterFunction.DropShadow? {
        val parts = args.split("""\s+""".toRegex())

        if (parts.size < 2) return null

        val offsetX = LengthParser.parse(parts[0]) ?: return null
        val offsetY = LengthParser.parse(parts[1]) ?: return null

        var blurRadius: app.irmodels.IRLength? = null
        var color: app.irmodels.IRColor? = null

        // Parse optional blur and color
        if (parts.size > 2) {
            // Try to parse as length (blur radius)
            LengthParser.parse(parts[2])?.let {
                blurRadius = it
                // If there's a 4th part, try to parse as color
                if (parts.size > 3) {
                    color = ColorParser.parse(parts.subList(3, parts.size).joinToString(" "))
                }
            } ?: run {
                // Not a length, try to parse as color
                color = ColorParser.parse(parts.subList(2, parts.size).joinToString(" "))
            }
        }

        return FilterFunction.DropShadow(offsetX, offsetY, blurRadius, color)
    }
}
