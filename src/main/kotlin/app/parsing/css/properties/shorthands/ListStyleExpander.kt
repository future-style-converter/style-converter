package app.parsing.css.properties.shorthands

/**
 * Expands `list-style` shorthand into longhands.
 * Syntax: <list-style-type> || <list-style-position> || <list-style-image>
 */
object ListStyleExpander : ShorthandExpander {

    private val positionKeywords = setOf("inside", "outside")
    private val typeKeywords = setOf(
        "disc", "circle", "square", "decimal", "decimal-leading-zero",
        "lower-roman", "upper-roman", "lower-greek", "lower-latin", "upper-latin",
        "armenian", "georgian", "lower-alpha", "upper-alpha", "none"
    )

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed in setOf("inherit", "initial", "unset", "revert")) {
            return mapOf(
                "list-style-type" to trimmed,
                "list-style-position" to trimmed,
                "list-style-image" to trimmed
            )
        }

        val tokens = tokenize(trimmed)
        var type: String? = null
        var position: String? = null
        var image: String? = null

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower in positionKeywords -> position = token
                lower.startsWith("url(") -> image = token
                lower == "none" && image == null && type != null -> image = "none"
                lower in typeKeywords || type == null -> type = token
            }
        }

        val result = mutableMapOf<String, String>()
        type?.let { result["list-style-type"] = it }
        position?.let { result["list-style-position"] = it }
        image?.let { result["list-style-image"] = it }
        return result
    }

    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        for (char in value) {
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char == ' ' && depth == 0 -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
