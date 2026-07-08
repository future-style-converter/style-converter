package app.parsing.css.properties.shorthands

/**
 * Expands `scroll-start` shorthand into scroll-start-block and scroll-start-inline.
 * Syntax: scroll-start-block scroll-start-inline
 */
object ScrollStartExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "scroll-start-block" to trimmed,
                "scroll-start-inline" to trimmed
            )
        }

        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        return when (parts.size) {
            1 -> mapOf(
                "scroll-start-block" to parts[0],
                "scroll-start-inline" to parts[0]
            )
            else -> mapOf(
                "scroll-start-block" to parts[0],
                "scroll-start-inline" to parts[1]
            )
        }
    }
}

/**
 * Expands `scroll-start-target` shorthand.
 */
object ScrollStartTargetExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "scroll-start-target-block" to trimmed,
                "scroll-start-target-inline" to trimmed
            )
        }

        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        return when (parts.size) {
            1 -> mapOf(
                "scroll-start-target-block" to parts[0],
                "scroll-start-target-inline" to parts[0]
            )
            else -> mapOf(
                "scroll-start-target-block" to parts[0],
                "scroll-start-target-inline" to parts[1]
            )
        }
    }
}
