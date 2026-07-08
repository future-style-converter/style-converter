package app.parsing.css.properties.shorthands

/**
 * Expands the `padding` shorthand property into individual side properties.
 *
 * Syntax:
 * - padding: <all>
 * - padding: <vertical> <horizontal>
 * - padding: <top> <horizontal> <bottom>
 * - padding: <top> <right> <bottom> <left>
 *
 * Examples:
 * - "10px" → all sides = 10px
 * - "10px 20px" → top/bottom = 10px, left/right = 20px
 * - "clamp(1rem, 3vw, 2rem)" → all sides = clamp(1rem, 3vw, 2rem)
 */
object PaddingExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val values = splitPreservingFunctions(value.trim())

        return when (values.size) {
            1 -> {
                // All sides same
                val all = values[0]
                mapOf(
                    "padding-top" to all,
                    "padding-right" to all,
                    "padding-bottom" to all,
                    "padding-left" to all
                )
            }
            2 -> {
                // Vertical | Horizontal
                val vertical = values[0]
                val horizontal = values[1]
                mapOf(
                    "padding-top" to vertical,
                    "padding-right" to horizontal,
                    "padding-bottom" to vertical,
                    "padding-left" to horizontal
                )
            }
            3 -> {
                // Top | Horizontal | Bottom
                val top = values[0]
                val horizontal = values[1]
                val bottom = values[2]
                mapOf(
                    "padding-top" to top,
                    "padding-right" to horizontal,
                    "padding-bottom" to bottom,
                    "padding-left" to horizontal
                )
            }
            4 -> {
                // Top | Right | Bottom | Left
                mapOf(
                    "padding-top" to values[0],
                    "padding-right" to values[1],
                    "padding-bottom" to values[2],
                    "padding-left" to values[3]
                )
            }
            else -> {
                // Invalid - return empty
                emptyMap()
            }
        }
    }

    /**
     * Split by whitespace while preserving content inside parentheses.
     * E.g., "clamp(1rem, 3vw, 2rem) 20px" → ["clamp(1rem, 3vw, 2rem)", "20px"]
     */
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
