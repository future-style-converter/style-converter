package app.parsing.css.properties.shorthands

/**
 * Expands the `text-decoration` shorthand property.
 *
 * Syntax: text-decoration: <line> <style>? <color>? <thickness>?
 * Order doesn't matter.
 *
 * Examples:
 * - "underline" → text-decoration-line: underline
 * - "underline dotted" → text-decoration-line: underline, text-decoration-style: dotted
 * - "underline dotted red" → text-decoration-line: underline, text-decoration-style: dotted, text-decoration-color: red
 */
object TextDecorationExpander : ShorthandExpander {
    private val lines = setOf("none", "underline", "overline", "line-through", "blink")
    private val styles = setOf("solid", "double", "dotted", "dashed", "wavy")

    override fun expand(value: String): Map<String, String> {
        val tokens = tokenize(value.trim())
        val result = mutableMapOf<String, String>()
        val lineValues = mutableListOf<String>()

        for (token in tokens) {
            when {
                token.lowercase() in lines -> lineValues.add(token)
                token.lowercase() in styles -> result["text-decoration-style"] = token
                token.startsWith("#") || token.startsWith("rgb") || token.startsWith("hsl") -> {
                    result["text-decoration-color"] = token
                }
                token.matches("""^\d+\.?\d*(px|em|rem|%|pt)?$""".toRegex()) -> {
                    result["text-decoration-thickness"] = token
                }
                token.matches("""^[a-zA-Z]+$""".toRegex()) && token.lowercase() !in lines && token.lowercase() !in styles -> {
                    // Named color
                    result["text-decoration-color"] = token
                }
            }
        }

        if (lineValues.isNotEmpty()) {
            result["text-decoration-line"] = lineValues.joinToString(" ")
        }

        return result
    }

    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                }
                char.isWhitespace() && parenDepth == 0 -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }
}
