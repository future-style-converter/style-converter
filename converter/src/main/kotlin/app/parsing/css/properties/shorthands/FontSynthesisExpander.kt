package app.parsing.css.properties.shorthands

/**
 * Expands `font-synthesis` shorthand into font-synthesis-* longhands.
 * Syntax: none | [weight] [style] [small-caps] [position]
 */
object FontSynthesisExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        if (lower in globalKeywords) {
            return mapOf(
                "font-synthesis-weight" to trimmed,
                "font-synthesis-style" to trimmed,
                "font-synthesis-small-caps" to trimmed,
                "font-synthesis-position" to trimmed
            )
        }

        if (lower == "none") {
            return mapOf(
                "font-synthesis-weight" to "none",
                "font-synthesis-style" to "none",
                "font-synthesis-small-caps" to "none",
                "font-synthesis-position" to "none"
            )
        }

        val result = mutableMapOf<String, String>()
        val tokens = lower.split(Regex("\\s+"))

        // Default all to none, then enable specified ones
        result["font-synthesis-weight"] = if ("weight" in tokens) "auto" else "none"
        result["font-synthesis-style"] = if ("style" in tokens) "auto" else "none"
        result["font-synthesis-small-caps"] = if ("small-caps" in tokens) "auto" else "none"
        result["font-synthesis-position"] = if ("position" in tokens) "auto" else "none"

        return result
    }
}
