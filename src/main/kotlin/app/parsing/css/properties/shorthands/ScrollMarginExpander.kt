package app.parsing.css.properties.shorthands

/**
 * Expands the `scroll-margin` shorthand property.
 *
 * Syntax: scroll-margin: <top> <right>? <bottom>? <left>?
 * Uses same 1-4 value syntax as margin/padding.
 */
object ScrollMarginExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-margin-top" to parts[0],
                "scroll-margin-right" to parts[0],
                "scroll-margin-bottom" to parts[0],
                "scroll-margin-left" to parts[0]
            )
            2 -> mapOf(
                "scroll-margin-top" to parts[0],
                "scroll-margin-right" to parts[1],
                "scroll-margin-bottom" to parts[0],
                "scroll-margin-left" to parts[1]
            )
            3 -> mapOf(
                "scroll-margin-top" to parts[0],
                "scroll-margin-right" to parts[1],
                "scroll-margin-bottom" to parts[2],
                "scroll-margin-left" to parts[1]
            )
            4 -> mapOf(
                "scroll-margin-top" to parts[0],
                "scroll-margin-right" to parts[1],
                "scroll-margin-bottom" to parts[2],
                "scroll-margin-left" to parts[3]
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
 * Expands the `scroll-margin-block` shorthand property.
 */
object ScrollMarginBlockExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-margin-block-start" to parts[0],
                "scroll-margin-block-end" to parts[0]
            )
            2 -> mapOf(
                "scroll-margin-block-start" to parts[0],
                "scroll-margin-block-end" to parts[1]
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
 * Expands the `scroll-margin-inline` shorthand property.
 */
object ScrollMarginInlineExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = splitPreservingFunctions(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-margin-inline-start" to parts[0],
                "scroll-margin-inline-end" to parts[0]
            )
            2 -> mapOf(
                "scroll-margin-inline-start" to parts[0],
                "scroll-margin-inline-end" to parts[1]
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
