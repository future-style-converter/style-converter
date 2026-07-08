package app.parsing.css.properties.shorthands

/**
 * Expands the `gap` shorthand property.
 *
 * Syntax: gap: <row-gap> <column-gap>?
 *
 * Examples:
 * - "10px" → row-gap: 10px, column-gap: 10px
 * - "10px 20px" → row-gap: 10px, column-gap: 20px
 */
object GapExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "row-gap" to parts[0],
                "column-gap" to parts[0]
            )
            2 -> mapOf(
                "row-gap" to parts[0],
                "column-gap" to parts[1]
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
