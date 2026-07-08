package app.parsing.css.properties.shorthands

/**
 * Expands the `margin-inline` shorthand property.
 *
 * Syntax: margin-inline: <start> <end>?
 *
 * Examples:
 * - "10px" → margin-inline-start: 10px, margin-inline-end: 10px
 * - "10px 20px" → margin-inline-start: 10px, margin-inline-end: 20px
 */
object MarginInlineExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "margin-inline-start" to parts[0],
                "margin-inline-end" to parts[0]
            )
            2 -> mapOf(
                "margin-inline-start" to parts[0],
                "margin-inline-end" to parts[1]
            )
            else -> emptyMap()
        }
    }

    private fun splitPreservingFunctions(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> { parenDepth++; current.append(char) }
                char == ')' -> { parenDepth--; current.append(char) }
                char.isWhitespace() && parenDepth == 0 -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
