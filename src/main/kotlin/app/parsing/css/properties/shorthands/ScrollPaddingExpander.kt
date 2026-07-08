package app.parsing.css.properties.shorthands

/**
 * Expands the `scroll-padding` shorthand property.
 *
 * Syntax: scroll-padding: <top> <right>? <bottom>? <left>?
 * Uses same 1-4 value syntax as margin/padding.
 */
object ScrollPaddingExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-padding-top" to parts[0],
                "scroll-padding-right" to parts[0],
                "scroll-padding-bottom" to parts[0],
                "scroll-padding-left" to parts[0]
            )
            2 -> mapOf(
                "scroll-padding-top" to parts[0],
                "scroll-padding-right" to parts[1],
                "scroll-padding-bottom" to parts[0],
                "scroll-padding-left" to parts[1]
            )
            3 -> mapOf(
                "scroll-padding-top" to parts[0],
                "scroll-padding-right" to parts[1],
                "scroll-padding-bottom" to parts[2],
                "scroll-padding-left" to parts[1]
            )
            4 -> mapOf(
                "scroll-padding-top" to parts[0],
                "scroll-padding-right" to parts[1],
                "scroll-padding-bottom" to parts[2],
                "scroll-padding-left" to parts[3]
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

/**
 * Expands the `scroll-padding-block` shorthand property.
 */
object ScrollPaddingBlockExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-padding-block-start" to parts[0],
                "scroll-padding-block-end" to parts[0]
            )
            2 -> mapOf(
                "scroll-padding-block-start" to parts[0],
                "scroll-padding-block-end" to parts[1]
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

/**
 * Expands the `scroll-padding-inline` shorthand property.
 */
object ScrollPaddingInlineExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-padding-inline-start" to parts[0],
                "scroll-padding-inline-end" to parts[0]
            )
            2 -> mapOf(
                "scroll-padding-inline-start" to parts[0],
                "scroll-padding-inline-end" to parts[1]
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
