package app.parsing.css.properties.shorthands

/**
 * Expands the `margin-block` shorthand property.
 *
 * Syntax: margin-block: <start> <end>?
 *
 * Examples:
 * - "10px" → margin-block-start: 10px, margin-block-end: 10px
 * - "10px 20px" → margin-block-start: 10px, margin-block-end: 20px
 */
object MarginBlockExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "margin-block-start" to parts[0],
                "margin-block-end" to parts[0]
            )
            2 -> mapOf(
                "margin-block-start" to parts[0],
                "margin-block-end" to parts[1]
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
