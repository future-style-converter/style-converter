package app.parsing.css.properties.shorthands

/**
 * Expands the `border-width` shorthand property.
 *
 * Syntax: border-width: <top> <right>? <bottom>? <left>?
 * Uses same 1-4 value syntax as margin/padding.
 *
 * Examples:
 * - "2px" → all sides = 2px
 * - "1px 2px" → top/bottom = 1px, left/right = 2px
 * - "1px 2px 3px" → top = 1px, left/right = 2px, bottom = 3px
 * - "1px 2px 3px 4px" → top = 1px, right = 2px, bottom = 3px, left = 4px
 */
object BorderWidthExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.trim().split("""\s+""".toRegex())

        return when (parts.size) {
            1 -> mapOf(
                "border-top-width" to parts[0],
                "border-right-width" to parts[0],
                "border-bottom-width" to parts[0],
                "border-left-width" to parts[0]
            )
            2 -> mapOf(
                "border-top-width" to parts[0],
                "border-right-width" to parts[1],
                "border-bottom-width" to parts[0],
                "border-left-width" to parts[1]
            )
            3 -> mapOf(
                "border-top-width" to parts[0],
                "border-right-width" to parts[1],
                "border-bottom-width" to parts[2],
                "border-left-width" to parts[1]
            )
            4 -> mapOf(
                "border-top-width" to parts[0],
                "border-right-width" to parts[1],
                "border-bottom-width" to parts[2],
                "border-left-width" to parts[3]
            )
            else -> emptyMap()
        }
    }
}
