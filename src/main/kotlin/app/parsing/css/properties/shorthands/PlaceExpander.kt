package app.parsing.css.properties.shorthands

/**
 * Expands the `place-content` shorthand property.
 *
 * Syntax: place-content: <align-content> <justify-content>?
 *
 * Examples:
 * - "center" → align-content: center, justify-content: center
 * - "start end" → align-content: start, justify-content: end
 */
object PlaceContentExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.trim().split("""\s+""".toRegex())

        return when (parts.size) {
            1 -> mapOf(
                "align-content" to parts[0],
                "justify-content" to parts[0]
            )
            2 -> mapOf(
                "align-content" to parts[0],
                "justify-content" to parts[1]
            )
            else -> emptyMap()
        }
    }
}

/**
 * Expands the `place-items` shorthand property.
 *
 * Syntax: place-items: <align-items> <justify-items>?
 *
 * Examples:
 * - "center" → align-items: center, justify-items: center
 * - "start end" → align-items: start, justify-items: end
 */
object PlaceItemsExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.trim().split("""\s+""".toRegex())

        return when (parts.size) {
            1 -> mapOf(
                "align-items" to parts[0],
                "justify-items" to parts[0]
            )
            2 -> mapOf(
                "align-items" to parts[0],
                "justify-items" to parts[1]
            )
            else -> emptyMap()
        }
    }
}

/**
 * Expands the `place-self` shorthand property.
 *
 * Syntax: place-self: <align-self> <justify-self>?
 *
 * Examples:
 * - "center" → align-self: center, justify-self: center
 * - "start end" → align-self: start, justify-self: end
 */
object PlaceSelfExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.trim().split("""\s+""".toRegex())

        return when (parts.size) {
            1 -> mapOf(
                "align-self" to parts[0],
                "justify-self" to parts[0]
            )
            2 -> mapOf(
                "align-self" to parts[0],
                "justify-self" to parts[1]
            )
            else -> emptyMap()
        }
    }
}
