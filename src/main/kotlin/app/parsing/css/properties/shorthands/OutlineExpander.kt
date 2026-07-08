package app.parsing.css.properties.shorthands

/**
 * Expands the `outline` shorthand property.
 *
 * Syntax: outline: <width> <style> <color>
 * Order doesn't matter.
 *
 * Examples:
 * - "2px solid #000" → width=2px, style=solid, color=#000
 * - "none" → width=none, style=none, color=none (or just style=none)
 * - "1px solid" → width=1px, style=solid
 * - "solid red" → style=solid, color=red
 */
object OutlineExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()

        // Special case: "none" means outline-style: none
        if (trimmed.lowercase() == "none") {
            return mapOf(
                "outline-style" to "none"
            )
        }

        // Parse outline value into width, style, and color components
        val parts = parseOutlineValue(trimmed)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["outline-width"] = it }
        parts["style"]?.let { result["outline-style"] = it }
        parts["color"]?.let { result["outline-color"] = it }

        return result
    }

    /**
     * Parse outline shorthand value into width, style, and color components.
     */
    private fun parseOutlineValue(value: String): Map<String, String> {
        val tokens = splitByWhitespace(value.trim())
        val result = mutableMapOf<String, String>()

        val outlineStyles = setOf("none", "hidden", "dotted", "dashed", "solid", "double",
                                  "groove", "ridge", "inset", "outset")

        for (token in tokens) {
            val tokenLower = token.lowercase()
            when {
                // Check if it's an outline style
                tokenLower in outlineStyles -> {
                    result["style"] = token
                }
                // Check if it's a width keyword
                tokenLower in setOf("thin", "medium", "thick") -> {
                    result["width"] = token
                }
                // Check if it's a length (width)
                token.matches("""^\d+\.?\d*(px|em|rem|%|pt|cm|mm|in|pc|ex|ch|vw|vh|vmin|vmax|fr)?$""".toRegex()) -> {
                    result["width"] = token
                }
                // Check if it's a color (hex, rgb, rgba, hsl, hsla, named)
                token.startsWith("#") ||
                tokenLower.startsWith("rgb") ||
                tokenLower.startsWith("hsl") ||
                tokenLower.startsWith("hwb") ||
                tokenLower.startsWith("lab") ||
                tokenLower.startsWith("lch") ||
                tokenLower.startsWith("oklab") ||
                tokenLower.startsWith("oklch") ||
                tokenLower.startsWith("color(") ||
                tokenLower.startsWith("color-mix") ||
                token.matches("""^[a-zA-Z]+$""".toRegex()) -> {
                    result["color"] = token
                }
            }
        }

        return result
    }

    /**
     * Split by whitespace while respecting parentheses.
     * This ensures "rgba(0, 0, 0, 0)" stays as one token.
     */
    private fun splitByWhitespace(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    depth++
                    current.append(char)
                }
                char == ')' -> {
                    depth--
                    current.append(char)
                }
                char.isWhitespace() && depth == 0 -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }
}
