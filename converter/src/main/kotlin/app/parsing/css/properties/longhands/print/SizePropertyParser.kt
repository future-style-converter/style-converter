package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.PageSizeValue
import app.irmodels.properties.print.SizeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object SizePropertyParser : PropertyParser {
    private val namedSizes = setOf(
        "a3", "a4", "a5", "b4", "b5", "jis-b4", "jis-b5",
        "letter", "legal", "ledger"
    )
    private val orientations = setOf("portrait", "landscape")

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "auto") {
            return SizeProperty(PageSizeValue.Auto)
        }

        val parts = trimmed.split(Regex("\\s+"))

        // Check for named size with optional orientation
        if (parts[0] in namedSizes) {
            val orientation = if (parts.size > 1 && parts[1] in orientations) parts[1] else null
            return SizeProperty(PageSizeValue.Named(parts[0], orientation))
        }

        // Check for orientation only
        if (parts[0] in orientations) {
            return SizeProperty(PageSizeValue.Named("auto", parts[0]))
        }

        // Parse as dimensions
        val width = LengthParser.parse(parts[0]) ?: return null
        val height = if (parts.size > 1) LengthParser.parse(parts[1]) else null

        return SizeProperty(PageSizeValue.Dimensions(width, height))
    }
}
