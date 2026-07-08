package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.AnchorNameProperty
import app.irmodels.properties.layout.advanced.AnchorNameValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `anchor-name` property.
 *
 * Syntax: none | <dashed-ident>+
 *
 * Examples:
 * - "none" → None
 * - "--my-anchor" → Single("--my-anchor")
 * - "--anchor1, --anchor2" → Multiple(["--anchor1", "--anchor2"])
 */
object AnchorNamePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        if (trimmed.lowercase() == "none") {
            return AnchorNameProperty(AnchorNameValue.None)
        }

        // Handle comma-separated or space-separated multiple names
        val names = if (trimmed.contains(",")) {
            trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else if (trimmed.contains(" ")) {
            trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        } else {
            listOf(trimmed)
        }

        val anchorValue = if (names.size == 1) {
            AnchorNameValue.Single(names[0])
        } else {
            AnchorNameValue.Multiple(names)
        }

        return AnchorNameProperty(anchorValue)
    }
}
