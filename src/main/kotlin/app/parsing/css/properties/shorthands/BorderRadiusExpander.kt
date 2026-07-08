package app.parsing.css.properties.shorthands

/**
 * Expands the `border-radius` shorthand property into individual corner properties.
 *
 * Syntax:
 * - border-radius: <all>
 * - border-radius: <top-left-bottom-right> <top-right-bottom-left>
 * - border-radius: <top-left> <top-right-bottom-left> <bottom-right>
 * - border-radius: <top-left> <top-right> <bottom-right> <bottom-left>
 *
 * Elliptical syntax (horizontal / vertical):
 * - border-radius: 10px / 20px → all corners with different h/v radii
 * - border-radius: 10px 20px / 5px 10px → different corners with different h/v radii
 *
 * Examples:
 * - "8px" → all corners = 8px
 * - "8px 4px" → top-left/bottom-right = 8px, top-right/bottom-left = 4px
 * - "10px / 20px" → all corners = 10px 20px (elliptical)
 */
object BorderRadiusExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()

        // Check for elliptical syntax with "/"
        if (trimmed.contains("/")) {
            return expandElliptical(trimmed)
        }

        return expandSimple(trimmed)
    }

    private fun expandSimple(value: String): Map<String, String> {
        val values = value.split("""\s+""".toRegex())

        return when (values.size) {
            1 -> {
                val all = values[0]
                mapOf(
                    "border-top-left-radius" to all,
                    "border-top-right-radius" to all,
                    "border-bottom-right-radius" to all,
                    "border-bottom-left-radius" to all
                )
            }
            2 -> {
                val topLeftBottomRight = values[0]
                val topRightBottomLeft = values[1]
                mapOf(
                    "border-top-left-radius" to topLeftBottomRight,
                    "border-top-right-radius" to topRightBottomLeft,
                    "border-bottom-right-radius" to topLeftBottomRight,
                    "border-bottom-left-radius" to topRightBottomLeft
                )
            }
            3 -> {
                val topLeft = values[0]
                val topRightBottomLeft = values[1]
                val bottomRight = values[2]
                mapOf(
                    "border-top-left-radius" to topLeft,
                    "border-top-right-radius" to topRightBottomLeft,
                    "border-bottom-right-radius" to bottomRight,
                    "border-bottom-left-radius" to topRightBottomLeft
                )
            }
            4 -> {
                mapOf(
                    "border-top-left-radius" to values[0],
                    "border-top-right-radius" to values[1],
                    "border-bottom-right-radius" to values[2],
                    "border-bottom-left-radius" to values[3]
                )
            }
            else -> emptyMap()
        }
    }

    private fun expandElliptical(value: String): Map<String, String> {
        val parts = value.split("/").map { it.trim() }
        if (parts.size != 2) return emptyMap()

        val horizontal = parts[0].split("""\s+""".toRegex())
        val vertical = parts[1].split("""\s+""".toRegex())

        // Expand each side to 4 values
        val h = expandToFour(horizontal)
        val v = expandToFour(vertical)

        if (h.isEmpty() || v.isEmpty()) return emptyMap()

        return mapOf(
            "border-top-left-radius" to "${h[0]} ${v[0]}",
            "border-top-right-radius" to "${h[1]} ${v[1]}",
            "border-bottom-right-radius" to "${h[2]} ${v[2]}",
            "border-bottom-left-radius" to "${h[3]} ${v[3]}"
        )
    }

    private fun expandToFour(values: List<String>): List<String> {
        return when (values.size) {
            1 -> listOf(values[0], values[0], values[0], values[0])
            2 -> listOf(values[0], values[1], values[0], values[1])
            3 -> listOf(values[0], values[1], values[2], values[1])
            4 -> values
            else -> emptyList()
        }
    }
}
