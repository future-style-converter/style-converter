package app.parsing.css.properties.shorthands

/**
 * Expands the `flex-flow` shorthand property.
 *
 * Syntax: flex-flow: <direction> <wrap>?
 *
 * Examples:
 * - "row" → flex-direction: row
 * - "row wrap" → flex-direction: row, flex-wrap: wrap
 * - "column nowrap" → flex-direction: column, flex-wrap: nowrap
 */
object FlexFlowExpander : ShorthandExpander {
    private val directions = setOf("row", "row-reverse", "column", "column-reverse")
    private val wraps = setOf("nowrap", "wrap", "wrap-reverse")

    override fun expand(value: String): Map<String, String> {
        val parts = value.trim().lowercase().split("""\s+""".toRegex())
        val result = mutableMapOf<String, String>()

        for (part in parts) {
            when {
                part in directions -> result["flex-direction"] = part
                part in wraps -> result["flex-wrap"] = part
            }
        }

        return result
    }
}
