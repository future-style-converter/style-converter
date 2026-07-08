package app.parsing.css.properties.shorthands

/**
 * Expands the `overflow` shorthand property.
 *
 * Syntax: overflow: <x> <y>?
 *
 * Examples:
 * - "hidden" → overflow-x: hidden, overflow-y: hidden
 * - "auto scroll" → overflow-x: auto, overflow-y: scroll
 */
object OverflowExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "overflow-x" to parts[0],
                "overflow-y" to parts[0]
            )
            2 -> mapOf(
                "overflow-x" to parts[0],
                "overflow-y" to parts[1]
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
