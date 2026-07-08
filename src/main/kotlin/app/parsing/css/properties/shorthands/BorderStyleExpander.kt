package app.parsing.css.properties.shorthands

/**
 * Expands the `border-style` shorthand property.
 *
 * Syntax: border-style: <top> <right>? <bottom>? <left>?
 * Uses same 1-4 value syntax as margin/padding.
 *
 * Examples:
 * - "solid" → all sides = solid
 * - "solid dashed" → top/bottom = solid, left/right = dashed
 * - "solid dashed dotted" → top = solid, left/right = dashed, bottom = dotted
 * - "solid dashed dotted double" → top = solid, right = dashed, bottom = dotted, left = double
 */
object BorderStyleExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.trim().split("""\s+""".toRegex())

        return when (parts.size) {
            1 -> mapOf(
                "border-top-style" to parts[0],
                "border-right-style" to parts[0],
                "border-bottom-style" to parts[0],
                "border-left-style" to parts[0]
            )
            2 -> mapOf(
                "border-top-style" to parts[0],
                "border-right-style" to parts[1],
                "border-bottom-style" to parts[0],
                "border-left-style" to parts[1]
            )
            3 -> mapOf(
                "border-top-style" to parts[0],
                "border-right-style" to parts[1],
                "border-bottom-style" to parts[2],
                "border-left-style" to parts[1]
            )
            4 -> mapOf(
                "border-top-style" to parts[0],
                "border-right-style" to parts[1],
                "border-bottom-style" to parts[2],
                "border-left-style" to parts[3]
            )
            else -> emptyMap()
        }
    }
}
