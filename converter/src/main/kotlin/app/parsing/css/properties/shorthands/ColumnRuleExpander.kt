package app.parsing.css.properties.shorthands

/**
 * Expands the `column-rule` shorthand property.
 *
 * Syntax: column-rule: <width> <style> <color>
 * Order doesn't matter.
 *
 * Examples:
 * - "1px solid black" → column-rule-width: 1px, column-rule-style: solid, column-rule-color: black
 * - "dotted red" → column-rule-style: dotted, column-rule-color: red
 */
object ColumnRuleExpander : ShorthandExpander {
    private val styles = setOf("none", "hidden", "dotted", "dashed", "solid", "double",
                               "groove", "ridge", "inset", "outset")

    override fun expand(value: String): Map<String, String> {
        val tokens = tokenize(value.trim())
        val result = mutableMapOf<String, String>()

        for (token in tokens) {
            when {
                token.lowercase() in styles -> result["column-rule-style"] = token
                token.matches("""^\d+\.?\d*(px|em|rem|%|pt|cm|mm|in|pc|ex|ch|vw|vh|vmin|vmax)?$""".toRegex()) -> {
                    result["column-rule-width"] = token
                }
                token.startsWith("#") || token.startsWith("rgb") || token.startsWith("hsl") -> {
                    result["column-rule-color"] = token
                }
                token.matches("""^[a-zA-Z]+$""".toRegex()) && token.lowercase() !in styles -> {
                    result["column-rule-color"] = token
                }
            }
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
