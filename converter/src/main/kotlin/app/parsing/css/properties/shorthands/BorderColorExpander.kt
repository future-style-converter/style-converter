package app.parsing.css.properties.shorthands

/**
 * Expands the `border-color` shorthand property.
 *
 * Syntax: border-color: <color>{1,4}
 *
 * Examples:
 * - "red" → all sides red
 * - "red blue" → top/bottom=red, left/right=blue
 * - "red blue green" → top=red, left/right=blue, bottom=green
 * - "red blue green yellow" → top=red, right=blue, bottom=green, left=yellow
 */
object BorderColorExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        // Use smart tokenizer that respects parentheses in color functions like rgba()
        val values = tokenizeColors(value.trim())

        return when (values.size) {
            1 -> {
                // Same color for all sides
                val color = values[0]
                mapOf(
                    "border-top-color" to color,
                    "border-right-color" to color,
                    "border-bottom-color" to color,
                    "border-left-color" to color
                )
            }
            2 -> {
                // [0] = top/bottom, [1] = left/right
                mapOf(
                    "border-top-color" to values[0],
                    "border-right-color" to values[1],
                    "border-bottom-color" to values[0],
                    "border-left-color" to values[1]
                )
            }
            3 -> {
                // [0] = top, [1] = left/right, [2] = bottom
                mapOf(
                    "border-top-color" to values[0],
                    "border-right-color" to values[1],
                    "border-bottom-color" to values[2],
                    "border-left-color" to values[1]
                )
            }
            4 -> {
                // [0] = top, [1] = right, [2] = bottom, [3] = left
                mapOf(
                    "border-top-color" to values[0],
                    "border-right-color" to values[1],
                    "border-bottom-color" to values[2],
                    "border-left-color" to values[3]
                )
            }
            else -> emptyMap()
        }
    }

    /**
     * Tokenize color values, respecting parentheses in color functions.
     * Example: "rgba(255, 255, 255, 0.3) #000" → ["rgba(255, 255, 255, 0.3)", "#000"]
     */
    private fun tokenizeColors(value: String): List<String> {
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
