package app.parsing.css.properties.shorthands

/**
 * Expands the `inset` shorthand property.
 *
 * Syntax: inset: <top> <right>? <bottom>? <left>?
 * Uses same 1-4 value syntax as margin/padding.
 *
 * Examples:
 * - "10px" → top/right/bottom/left = 10px
 * - "10px 20px" → top/bottom = 10px, left/right = 20px
 * - "10px 20px 15px" → top = 10px, left/right = 20px, bottom = 15px
 * - "10px 20px 15px 5px" → top = 10px, right = 20px, bottom = 15px, left = 5px
 */
object InsetExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "top" to parts[0],
                "right" to parts[0],
                "bottom" to parts[0],
                "left" to parts[0]
            )
            2 -> mapOf(
                "top" to parts[0],
                "right" to parts[1],
                "bottom" to parts[0],
                "left" to parts[1]
            )
            3 -> mapOf(
                "top" to parts[0],
                "right" to parts[1],
                "bottom" to parts[2],
                "left" to parts[1]
            )
            4 -> mapOf(
                "top" to parts[0],
                "right" to parts[1],
                "bottom" to parts[2],
                "left" to parts[3]
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
