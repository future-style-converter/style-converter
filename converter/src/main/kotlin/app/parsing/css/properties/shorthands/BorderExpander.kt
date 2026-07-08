package app.parsing.css.properties.shorthands

/**
 * Expands the `border` shorthand property into width, style, and color for all sides.
 *
 * Syntax: border: <width> <style> <color>
 * Order doesn't matter, but typically: width style color
 *
 * Examples:
 * - "2px solid #000" → width=2px, style=solid, color=#000 (all sides)
 * - "1px solid" → width=1px, style=solid (all sides)
 * - "solid red" → style=solid, color=red (all sides)
 */
object BorderExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        // First, extract width, style, and color from the value
        val parts = parseBorderValue(value)

        // Then expand to all 4 sides
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { width ->
            result["border-top-width"] = width
            result["border-right-width"] = width
            result["border-bottom-width"] = width
            result["border-left-width"] = width
        }

        parts["style"]?.let { style ->
            result["border-top-style"] = style
            result["border-right-style"] = style
            result["border-bottom-style"] = style
            result["border-left-style"] = style
        }

        parts["color"]?.let { color ->
            result["border-top-color"] = color
            result["border-right-color"] = color
            result["border-bottom-color"] = color
            result["border-left-color"] = color
        }

        return result
    }

    /**
     * Parse border shorthand value into width, style, and color components.
     */
    private fun parseBorderValue(value: String): Map<String, String> {
        // Use smart tokenizer that respects parentheses in color functions
        val tokens = tokenizeBorderValue(value.trim())
        val result = mutableMapOf<String, String>()

        val borderStyles = setOf("none", "hidden", "dotted", "dashed", "solid", "double",
                                 "groove", "ridge", "inset", "outset")

        for (token in tokens) {
            when {
                // Check if it's a border style
                token.lowercase() in borderStyles -> {
                    result["style"] = token
                }
                // Check if it's a length (width)
                token.matches("""^\d+\.?\d*(px|em|rem|%|pt|cm|mm|in|pc|ex|ch|vw|vh|vmin|vmax|fr)?$""".toRegex()) -> {
                    result["width"] = token
                }
                // Check if it's a color (hex, rgb, named)
                token.startsWith("#") ||
                token.startsWith("rgb") ||
                token.startsWith("hsl") ||
                token.matches("""^[a-zA-Z]+$""".toRegex()) -> {
                    result["color"] = token
                }
            }
        }

        return result
    }

    /**
     * Tokenize border value, respecting parentheses in color functions.
     * Example: "1px solid rgba(255, 255, 255, 0.2)" → ["1px", "solid", "rgba(255, 255, 255, 0.2)"]
     */
    private fun tokenizeBorderValue(value: String): List<String> {
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

/**
 * Expands individual border side shorthands like `border-top`, `border-right`, etc.
 */
object BorderTopExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = BorderExpander.parseBorderValue(value)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["border-top-width"] = it }
        parts["style"]?.let { result["border-top-style"] = it }
        parts["color"]?.let { result["border-top-color"] = it }

        return result
    }
}

object BorderRightExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = BorderExpander.parseBorderValue(value)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["border-right-width"] = it }
        parts["style"]?.let { result["border-right-style"] = it }
        parts["color"]?.let { result["border-right-color"] = it }

        return result
    }
}

object BorderBottomExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = BorderExpander.parseBorderValue(value)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["border-bottom-width"] = it }
        parts["style"]?.let { result["border-bottom-style"] = it }
        parts["color"]?.let { result["border-bottom-color"] = it }

        return result
    }
}

object BorderLeftExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = BorderExpander.parseBorderValue(value)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["border-left-width"] = it }
        parts["style"]?.let { result["border-left-style"] = it }
        parts["color"]?.let { result["border-left-color"] = it }

        return result
    }
}

// Make parseBorderValue and tokenizer accessible to side expanders
private fun BorderExpander.parseBorderValue(value: String): Map<String, String> {
    val tokens = tokenizeBorderValue(value.trim())
    val result = mutableMapOf<String, String>()

    val borderStyles = setOf("none", "hidden", "dotted", "dashed", "solid", "double",
                             "groove", "ridge", "inset", "outset")

    for (token in tokens) {
        when {
            token.lowercase() in borderStyles -> result["style"] = token
            token.matches("""^\d+\.?\d*(px|em|rem|%|pt|cm|mm|in|pc|ex|ch|vw|vh|vmin|vmax|fr)?$""".toRegex()) -> result["width"] = token
            token.startsWith("#") || token.startsWith("rgb") || token.startsWith("hsl") || token.matches("""^[a-zA-Z]+$""".toRegex()) -> result["color"] = token
        }
    }

    return result
}

private fun tokenizeBorderValue(value: String): List<String> {
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
