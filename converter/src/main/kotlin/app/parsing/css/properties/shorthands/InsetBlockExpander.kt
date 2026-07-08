package app.parsing.css.properties.shorthands

/**
 * Expands the `inset-block` shorthand property.
 *
 * Syntax: inset-block: <start> <end>?
 *
 * Examples:
 * - "10px" → inset-block-start: 10px, inset-block-end: 10px
 * - "10px 20px" → inset-block-start: 10px, inset-block-end: 20px
 */
object InsetBlockExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "inset-block-start" to parts[0],
                "inset-block-end" to parts[0]
            )
            2 -> mapOf(
                "inset-block-start" to parts[0],
                "inset-block-end" to parts[1]
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
