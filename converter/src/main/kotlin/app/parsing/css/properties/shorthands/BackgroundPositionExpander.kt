package app.parsing.css.properties.shorthands

/**
 * Expands the `background-position` shorthand property.
 *
 * Syntax: background-position: <x> <y>?
 *
 * Examples:
 * - "center" → background-position-x: center, background-position-y: center
 * - "left top" → background-position-x: left, background-position-y: top
 * - "50% 25%" → background-position-x: 50%, background-position-y: 25%
 * - "10px 20px" → background-position-x: 10px, background-position-y: 20px
 * - "right 10px bottom 20px" → (4-value syntax, preserved as-is)
 * - "calc(100% - 20px) calc(100% - 10px)" → (preserves calc expressions)
 */
object BackgroundPositionExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()

        // Handle comma-separated multiple backgrounds by taking the first one
        val firstPosition = if (trimmed.contains(",")) {
            splitByComma(trimmed).firstOrNull()?.trim() ?: trimmed
        } else {
            trimmed
        }

        val parts = splitPreservingFunctions(firstPosition)

        return when (parts.size) {
            1 -> {
                // Single value: if it's a horizontal keyword, y defaults to center
                // if it's a vertical keyword, x defaults to center
                // otherwise both get the same value
                val v = parts[0]
                when (v.lowercase()) {
                    "left", "right" -> mapOf(
                        "background-position-x" to v,
                        "background-position-y" to "center"
                    )
                    "top", "bottom" -> mapOf(
                        "background-position-x" to "center",
                        "background-position-y" to v
                    )
                    else -> mapOf(
                        "background-position-x" to v,
                        "background-position-y" to v
                    )
                }
            }
            2 -> mapOf(
                "background-position-x" to parts[0],
                "background-position-y" to parts[1]
            )
            4 -> {
                // 4-value syntax: e.g., "right 10px bottom 20px"
                // Edge + offset pairs - pass through as-is
                mapOf(
                    "background-position-x" to "${parts[0]} ${parts[1]}",
                    "background-position-y" to "${parts[2]} ${parts[3]}"
                )
            }
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

    private fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> { parenDepth++; current.append(char) }
                char == ')' -> { parenDepth--; current.append(char) }
                char == ',' && parenDepth == 0 -> {
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
